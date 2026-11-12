package com.khang.jobtrack.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.AiClient;
import com.khang.jobtrack.ai.TaskSuggestion;
import com.khang.jobtrack.dto.AiTaskForm;
import com.khang.jobtrack.dto.TaskForm;
import com.khang.jobtrack.entity.AiResult;
import com.khang.jobtrack.entity.AiResultType;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.AiResultRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 求人票（応募の jd_text）からAIで締切を読み取り、タスク候補を作る。
 * 流れ：buildPrompt → (キャッシュ or AI) → parse → 画面で確認 → saveSelected
 */
@Service
public class AiTaskService {

    /** 1回で提案するタスクの上限 */
    public static final int MAX_SUGGESTIONS = 10;

    private final ApplicationService applicationService;
    private final TaskService taskService;
    private final AiClient aiClient;
    private final AiResultRepository aiResultRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AiTaskService(ApplicationService applicationService, TaskService taskService,
                         AiClient aiClient, AiResultRepository aiResultRepository) {
        this.applicationService = applicationService;
        this.taskService = taskService;
        this.aiClient = aiClient;
        this.aiResultRepository = aiResultRepository;
    }

    // ===================================================================
    // 画面から呼ばれる処理
    // ===================================================================

    public List<TaskSuggestion> suggest(User user, Long applicationId, LocalDate today) {
        Application application = applicationService.findOwned(user, applicationId); // 他人の応募なら404
        String jdText = application.getJdText();
        if (jdText == null || jdText.isBlank()) {
            throw new AiException("この応募には求人票が登録されていません。応募の編集画面で求人票を貼り付けてください。");
        }
        String prompt = buildPrompt(jdText, today);
        String json = findOrCallAi(application, prompt);
        return parse(json, today);
    }

    /** チェックされた候補だけをタスクとして保存する。保存した件数を返す */
    public int saveSelected(User user, Long applicationId, AiTaskForm form) {
        int count = 0;
        for (AiTaskForm.Item item : form.getItems()) {
            if (!item.isSelected()) {
                continue;
            }
            TaskForm taskForm = new TaskForm();
            taskForm.setApplicationId(applicationId);
            taskForm.setTitle(item.getTitle());
            taskForm.setDueDate(item.getDueDate());
            taskService.create(user, taskForm); // ここでも応募の持ち主をチェックしている
            count++;
        }
        return count;
    }

    // ===================================================================
    // プロンプト・回答の検証・キャッシュ
    // ===================================================================

    /**
     * AIへの指示文（プロンプト）を作る。
     * 条件：
     *   - 求人票（jdText）をそのまま含める
     *   - 今日の日付（yyyy-MM-dd）を含める →「来週金曜」「11月末」などを日付に直してもらうため
     *   - 返してほしいJSONの形を例で示す：{"tasks":[{"title":"...","dueDate":"yyyy-MM-dd"}]}
     *   - 締切が見つからなければ {"tasks":[]} を返すように書く（AIに作り話をさせない）
     *   - 「JSON」という単語を必ず入れる（Groq の JSON モードの条件）
     * ヒント：Java のテキストブロック（"""..."""）と formatted(...) が便利
     */
    public String buildPrompt(String jdText, LocalDate today) {
        return """
                あなたは就職活動中の学生を支援するアシスタントです。
                次の求人票を読み、応募者が期限までに行う必要がある作業（ES提出、Webテスト受検、説明会の予約、応募締切など）を抽出してください。

                ルール：
                - 今日の日付は %s です。
                - 年が書かれていない日付は、今日以降で最も近い日付にしてください。
                - 「来週金曜」「今月末」などの表現も、今日の日付をもとに具体的な日付にしてください。
                - 日付がはっきり書かれていない作業や、すでに過ぎた日付は含めないでください。
                - 求人票に書かれていない作業や日付を作らないでください。
                - title は応募者がやることを30文字以内の日本語で書いてください（例：「ES提出」「SPI受検」）。
                - dueDate は yyyy-MM-dd 形式にしてください。
                - 締切が1つも見つからない場合は {"tasks":[]} を返してください。

                回答は次の形式の JSON だけを返してください。説明文は不要です。
                {"tasks":[{"title":"ES提出","dueDate":"2026-11-20"}]}

                求人票：
                %s
                """.formatted(today, jdText);
    }

    /**
     * AIの回答（JSON文字列）をタスク候補のリストに変換する。
     * ルール：
     *   - JSONとして読めない → AiException("AIの回答を読み取れませんでした。")
     *   - "tasks" がない／空 → 空のリスト
     *   - title が空 → その候補は捨てる（前後の空白は trim）
     *   - dueDate が yyyy-MM-dd として読めない → 捨てる（AIは間違えることがある）
     *   - dueDate が今日より前 → 捨てる（過去の締切は登録しても意味がない）
     *   - 最大 MAX_SUGGESTIONS 件まで
     * ヒント：
     *   AiTaskJson root = jsonMapper.readValue(json, AiTaskJson.class);   // 失敗すると JacksonException
     *   LocalDate.parse("2026-11-20")                                      // 失敗すると DateTimeParseException
     */
    public List<TaskSuggestion> parse(String json, LocalDate today) {
        List<TaskSuggestion> result = new ArrayList<>();

        // ① JSON → record に変換（読めなければ利用者向けのエラー）
        AiTaskJson root;
        try {
            root = jsonMapper.readValue(json, AiTaskJson.class);
        } catch (JacksonException e) {
            throw new AiException("AIの回答を読み取れませんでした。もう一度お試しください。", e);
        }

        // ② "tasks" がない → 空のリスト
        if (root == null || root.tasks() == null) {
            return result;
        }

        // ③ 1件ずつチェックして、条件に合うものだけ残す
        for (AiTaskItem item : root.tasks()) {
            if (item == null) {
                continue;
            }

            // タイトル：null・空白だけ → 捨てる
            String title = item.title() == null ? "" : item.title().trim();
            if (title.isEmpty()) {
                continue;
            }

            // 締切：null・形式が違う → 捨てる
            if (item.dueDate() == null) {
                continue;
            }
            LocalDate dueDate;
            try {
                dueDate = LocalDate.parse(item.dueDate().trim());
            } catch (DateTimeParseException e) {
                continue;
            }

            // 過去の締切 → 捨てる（今日はOK）
            if (dueDate.isBefore(today)) {
                continue;
            }

            result.add(new TaskSuggestion(title, dueDate));
            if (result.size() >= MAX_SUGGESTIONS) {
                break;
            }
        }
        return result;
    }

    /**
     * 同じ応募・同じプロンプトで前にAIを呼んでいれば、その結果（ai_results）を再利用する。
     * なければ AI を呼び、結果を ai_results に保存してから返す。
     * ヒント：aiResultRepository.findFirstByApplicationIdAndTypeAndInputTextOrderByIdDesc(...)
     *         type は AiResultType.AUTO_FILL、inputText は prompt
     */
    private String findOrCallAi(Application application, String prompt) {
        // 前に同じ内容で呼んでいれば、保存した結果を使う（APIを呼ばない）
        Optional<AiResult> cached = aiResultRepository.findFirstByApplicationIdAndTypeAndInputTextOrderByIdDesc(
                application.getId(), AiResultType.AUTO_FILL, prompt);
        if (cached.isPresent()) {
            return cached.get().getOutputText();
        }

        // なければAIを呼び、結果を保存する
        String json = aiClient.generateJson(prompt);

        AiResult aiResult = new AiResult();
        aiResult.setApplication(application);
        aiResult.setType(AiResultType.AUTO_FILL);
        aiResult.setInputText(prompt);
        aiResult.setOutputText(json);
        aiResultRepository.save(aiResult);

        return json;
    }

    // ---- AIが返すJSONの形 {"tasks":[{"title":"...","dueDate":"..."}]} ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiTaskJson(List<AiTaskItem> tasks) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiTaskItem(String title, String dueDate) {}
}
