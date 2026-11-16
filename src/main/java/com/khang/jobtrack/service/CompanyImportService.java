package com.khang.jobtrack.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.khang.jobtrack.ai.AiClient;
import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.dto.CompanyImportForm;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.ApplicationStatus;
import com.khang.jobtrack.entity.Company;
import com.khang.jobtrack.entity.Task;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.ApplicationRepository;
import com.khang.jobtrack.repository.CompanyRepository;
import com.khang.jobtrack.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 説明会の案内・求人情報などの資料から、AIで企業ごとの情報を取り出し、
 * 確認画面で選んだものを「企業・応募・タスク（説明会）」としてまとめて登録する。
 * 流れ：buildPrompt → AI → parse → toFormItems → 画面で確認・修正 → save
 *
 * ※ ai_results は応募に紐づくので、まだ応募がない取り込みはキャッシュしない（1回きりの操作のため）
 */
@Service
public class CompanyImportService {

    public static final int MAX_TEXT_LENGTH = 20000;
    public static final int MAX_COMPANIES = 20;
    private static final Pattern TIME = Pattern.compile("([01]?\\d|2[0-3]):[0-5]\\d");
    private static final Pattern JLPT = Pattern.compile("N[1-5](以上)?");

    private final AiClient aiClient;
    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final TaskRepository taskRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public CompanyImportService(AiClient aiClient, CompanyRepository companyRepository,
                                ApplicationRepository applicationRepository, TaskRepository taskRepository) {
        this.aiClient = aiClient;
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.taskRepository = taskRepository;
    }

    /** AI が資料から取り出した1社分（チェック済み） */
    public record CompanyCandidate(String name, String industry, List<String> positions, String japanese,
                                   String foreignStudentNote, String location, LocalDate eventDate,
                                   String eventTime, String eventFormat, List<String> features) {
    }

    /** 確認画面に出すもの：企業の一覧と、注意メッセージ（なければ null） */
    public record ImportPreview(List<CompanyImportForm.Item> items, String warning) {
    }

    /** AIの回答を読んだ結果：企業の一覧と、AIが数えた資料の企業数（不明なら 0） */
    public record ParseResult(List<CompanyCandidate> companies, int totalCount) {
    }

    /** 登録結果（画面のメッセージ用） */
    public record ImportResult(int companiesCreated, int companiesReused, int applicationsCreated,
                               int applicationsReused, int tasksCreated) {
    }

    // ===================================================================
    // 取り出し
    // ===================================================================

    public ImportPreview extract(User user, String text, LocalDate today) {
        if (text == null || text.isBlank()) {
            throw new AiException("資料の内容がありません。ファイルを選ぶか、文章を貼り付けてください。");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new AiException("資料が長すぎます（" + MAX_TEXT_LENGTH + "文字まで）。必要な部分だけにしてください。");
        }
        String json = aiClient.generateJson(buildPrompt(text.trim(), today));
        ParseResult parsed = parse(json, today);
        if (parsed.companies().isEmpty()) {
            throw new AiException("資料から企業の情報が見つかりませんでした。");
        }
        // AIが途中の企業を省略することがあるので、AI自身が数えた企業数と比べて知らせる
        String warning = null;
        if (parsed.totalCount() > parsed.companies().size()) {
            warning = "資料には " + parsed.totalCount() + " 社ありますが、" + parsed.companies().size()
                    + " 社しか整理できませんでした。足りない企業は、その部分だけを貼り付けて、もう一度取り込んでください。";
        }
        return new ImportPreview(toFormItems(user, parsed.companies()), warning);
    }

    /**
     * プロンプトの考え方：
     * - 資料にないことは推測させない（空文字・空のリスト）
     * - 日付は今日をもとに年を補う（資料は「12/7(月)」のように年がないことが多い）
     * - 留学生に大事な「日本語の条件」「留学生向けの条件」を別の項目にする
     */
    public String buildPrompt(String text, LocalDate today) {
        return """
                あなたは日本で就職活動をしている留学生を支援するアシスタントです。
                次の資料（学校の企業説明会の案内、求人情報など）を読み、企業ごとに情報を整理してください。

                ルール：
                - 資料に載っている企業は、1社も省略せずにすべて出してください（①②③…のように番号がある場合は、すべての番号）。
                - totalCount には、資料に載っている企業の数を書いてください。
                - 資料に書かれている内容だけを使ってください。書かれていないことは推測せず、空文字 "" か空のリスト [] にしてください。
                - 今日の日付は %s です。年が書かれていない日付は、今日以降で最も近い日付にしてください。
                - name：企業名。敬称の「様」は取ってください。
                - industry：業界を20文字以内で（例：IT、通信インフラ、物流IT）。
                - positions：募集職種のリスト。職種名だけを短く書いてください（括弧の中の説明や番号は省く）。
                - japanese：留学生の日本語の条件（例：N2以上、N1以上）。日本語能力試験のレベルは必ずここに書いてください。
                - foreignStudentNote：留学生向けのその他の条件（例：AWSの学習経験が必要）。
                - location：勤務地。
                - eventDate：説明会などのイベントの日付（yyyy-MM-dd）。eventTime：開始時刻（HH:mm）。eventFormat：オンライン・対面など。
                - features：どんな会社か、短い文（30文字くらい）で3つまで。

                回答は次の形式の JSON だけを返してください。説明文は不要です。
                {"totalCount":0,"companies":[{"name":"","industry":"","positions":[""],"japanese":"","foreignStudentNote":"","location":"","eventDate":"","eventTime":"","eventFormat":"","features":[""]}]}

                資料：
                %s
                """.formatted(today, text);
    }

    /**
     * AIの回答（JSON）を企業のリストに変換する。
     * - JSONとして読めない → AiException
     * - name が空 → 捨てる。文字列は trim し、DBの長さに合わせて切る
     * - eventDate：形式が違う → null、今日より前 → null（終わった説明会のタスクは作らない）
     * - eventTime：HH:mm でなければ ""
     * - 最大 MAX_COMPANIES 社
     */
    public ParseResult parse(String json, LocalDate today) {
        ImportJson root;
        try {
            root = jsonMapper.readValue(json, ImportJson.class);
        } catch (JacksonException e) {
            throw new AiException("AIの回答を読み取れませんでした。もう一度お試しください。", e);
        }

        List<CompanyCandidate> result = new ArrayList<>();
        if (root == null || root.companies() == null) {
            return new ParseResult(result, 0);
        }
        for (CompanyJson c : root.companies()) {
            if (c == null) {
                continue;
            }
            String name = cut(c.name(), 100);
            if (name.isEmpty()) {
                continue;
            }
            String time = trim(c.eventTime());
            String japanese = cut(c.japanese(), 50);
            String note = cut(c.foreignStudentNote(), 300);
            // AIが「N1以上」を留学生の条件の方に入れることがある → 日本語の条件に移す
            if (japanese.isEmpty()) {
                java.util.regex.Matcher m = JLPT.matcher(note);
                if (m.find()) {
                    japanese = m.group();
                    note = note.equals(m.group()) ? "" : note;
                }
            }
            result.add(new CompanyCandidate(
                    name,
                    cut(c.industry(), 50),
                    cleanList(c.positions(), 10),
                    japanese,
                    note,
                    cut(c.location(), 200),
                    parseFutureDate(c.eventDate(), today),
                    TIME.matcher(time).matches() ? time : "",
                    cut(c.eventFormat(), 20),
                    cleanList(c.features(), 3)));
            if (result.size() >= MAX_COMPANIES) {
                break;
            }
        }
        int total = root.totalCount() == null ? 0 : Math.min(root.totalCount(), MAX_COMPANIES);
        return new ParseResult(result, total);
    }

    /** 確認画面のフォームに変換（メモ・求人票・タスク名をここで組み立てる） */
    public List<CompanyImportForm.Item> toFormItems(User user, List<CompanyCandidate> candidates) {
        List<CompanyImportForm.Item> items = new ArrayList<>();
        for (CompanyCandidate c : candidates) {
            CompanyImportForm.Item item = new CompanyImportForm.Item();
            item.setName(c.name());
            item.setIndustry(c.industry());
            item.setPosition(joinPositions(c.positions()));
            item.setEventDate(c.eventDate());
            item.setTaskTitle(buildTaskTitle(c));
            item.setMemo(buildMemo(c));
            item.setJdText(buildJdText(c));
            boolean existing = companyRepository.existsByUserIdAndName(user.getId(), c.name());
            item.setExisting(existing);
            item.setSelected(!existing); // 登録済みの企業は、初期状態ではチェックしない（二重登録を防ぐ）
            if (existing) {
                // AIは毎回少し違う言い方で職種を書くので、既存の応募の職種を初期値にする（同じ職種なら応募を使い回す）
                List<String> positions = applicationRepository
                        .findByCompanyUserIdAndCompanyNameOrderByIdAsc(user.getId(), c.name())
                        .stream().map(Application::getPosition).distinct().toList();
                item.setExistingPositions(positions);
                if (!positions.isEmpty()) {
                    item.setPosition(positions.get(0));
                }
            }
            items.add(item);
        }
        return items;
    }

    /** 職種を「、」でつなぐ。100文字（DBの長さ）に入らない分は途中で切らずに「ほか」にする */
    static String joinPositions(List<String> positions) {
        if (positions.isEmpty()) {
            return "（職種未定）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < positions.size(); i++) {
            String next = (i == 0 ? "" : "、") + positions.get(i);
            if (sb.length() + next.length() > 97) { // 「ほか」の分を残す
                return sb.length() == 0 ? positions.get(0).substring(0, 97) + "ほか" : sb + "ほか";
            }
            sb.append(next);
        }
        return sb.toString();
    }

    String buildTaskTitle(CompanyCandidate c) {
        List<String> parts = new ArrayList<>();
        if (!c.eventTime().isEmpty()) {
            parts.add(c.eventTime());
        }
        if (!c.eventFormat().isEmpty()) {
            parts.add(c.eventFormat());
        }
        return cut(parts.isEmpty() ? "企業説明会" : "企業説明会（" + String.join("・", parts) + "）", 100);
    }

    String buildMemo(CompanyCandidate c) {
        StringBuilder sb = new StringBuilder();
        line(sb, "【日本語】", c.japanese());
        line(sb, "【留学生の条件】", c.foreignStudentNote());
        line(sb, "【勤務地】", c.location());
        for (String f : c.features()) {
            line(sb, "・", f);
        }
        sb.append("（資料から取り込み）");
        return sb.toString();
    }

    String buildJdText(CompanyCandidate c) {
        StringBuilder sb = new StringBuilder();
        line(sb, "募集職種：", String.join("、", c.positions()));
        line(sb, "日本語：", c.japanese());
        line(sb, "留学生の条件：", c.foreignStudentNote());
        line(sb, "勤務地：", c.location());
        if (c.eventDate() != null) {
            line(sb, "説明会：", (c.eventDate() + " " + c.eventTime() + " " + c.eventFormat()).trim());
        }
        if (!c.features().isEmpty()) {
            sb.append("会社の特徴：\n");
            for (String f : c.features()) {
                line(sb, "・", f);
            }
        }
        return sb.toString().trim();
    }

    // ===================================================================
    // 登録
    // ===================================================================

    /**
     * チェックした企業を登録する。途中で失敗したら全部取り消す（@Transactional）。
     * - 同じ名前の企業があればそれを使う（UNIQUE制約・重複登録を防ぐ。既存のメモは上書きしない）
     * - 同じ企業・同じ職種の応募があればそれを使う。なければ「未応募」で作り、求人票に整理した内容を入れる
     * - 説明会の日付があり、同じ応募に同じタスク（名前・日付）がなければ、タスクを作る
     * → 同じ資料を2回取り込んでも、データが二重にならない
     */
    @Transactional
    public ImportResult save(User user, CompanyImportForm form) {
        int created = 0;
        int reused = 0;
        int applicationsCreated = 0;
        int applicationsReused = 0;
        int tasks = 0;

        for (CompanyImportForm.Item item : form.getItems()) {
            if (!item.isSelected()) {
                continue;
            }
            Company company = companyRepository.findByUserIdAndName(user.getId(), item.getName()).orElse(null);
            if (company == null) {
                company = new Company();
                company.setUser(user);
                company.setName(item.getName());
                company.setIndustry(item.getIndustry() == null || item.getIndustry().isBlank() ? null : item.getIndustry());
                company.setMemo(item.getMemo());
                companyRepository.save(company);
                created++;
            } else {
                reused++;
            }

            Application application = company.getId() == null ? null
                    : applicationRepository.findFirstByCompanyIdAndPosition(company.getId(), item.getPosition())
                    .orElse(null);
            if (application == null) {
                application = new Application();
                application.setCompany(company);
                application.setPosition(item.getPosition());
                application.setStatus(ApplicationStatus.NOT_APPLIED);
                application.setJdText(item.getJdText());
                applicationRepository.save(application);
                applicationsCreated++;
            } else {
                applicationsReused++; // 既存の応募のステータスや求人票は変えない
            }

            if (item.getEventDate() != null) {
                String title = item.getTaskTitle() == null || item.getTaskTitle().isBlank()
                        ? "企業説明会" : item.getTaskTitle();
                boolean taskExists = application.getId() != null
                        && taskRepository.existsByApplicationIdAndTitleAndDueDate(
                        application.getId(), title, item.getEventDate());
                if (!taskExists) {
                    Task task = new Task();
                    task.setApplication(application);
                    task.setTitle(title);
                    task.setDueDate(item.getEventDate());
                    task.setDone(false);
                    taskRepository.save(task);
                    tasks++;
                }
            }
        }
        return new ImportResult(created, reused, applicationsCreated, applicationsReused, tasks);
    }

    // ===================================================================
    // 小さな部品
    // ===================================================================

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String cut(String s, int max) {
        String t = trim(s);
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static List<String> cleanList(List<String> items, int max) {
        List<String> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (String item : items) {
            String t = trim(item);
            if (!t.isEmpty()) {
                result.add(t);
            }
            if (result.size() >= max) {
                break;
            }
        }
        return result;
    }

    private static LocalDate parseFutureDate(String s, LocalDate today) {
        String t = trim(s);
        if (t.isEmpty()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(t);
            return date.isBefore(today) ? null : date;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(value).append('\n');
        }
    }

    // ---- AIが返すJSONの形 ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ImportJson(Integer totalCount, List<CompanyJson> companies) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompanyJson(String name, String industry, List<String> positions, String japanese,
                       String foreignStudentNote, String location, String eventDate, String eventTime,
                       String eventFormat, List<String> features) {}
}
