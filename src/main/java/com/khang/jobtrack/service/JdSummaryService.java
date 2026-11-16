package com.khang.jobtrack.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.JdSummary;
import com.khang.jobtrack.ai.SummaryLanguage;
import com.khang.jobtrack.entity.AiResultType;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.User;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 求人票（応募の jd_text）をAIで「やさしい日本語」または「ベトナム語」で解説する。
 * 流れ：buildPrompt → (キャッシュ or AI) → parse → 画面に表示
 */
@Service
public class JdSummaryService {

    /** 各項目の上限（多すぎると読みにくい） */
    public static final int MAX_ITEMS = 8;

    private final ApplicationService applicationService;
    private final AiResultCacheService aiCache;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public JdSummaryService(ApplicationService applicationService, AiResultCacheService aiCache) {
        this.applicationService = applicationService;
        this.aiCache = aiCache;
    }

    public JdSummary explain(User user, Long applicationId, SummaryLanguage language) {
        Application application = applicationService.findOwned(user, applicationId); // 他人の応募なら404
        String jdText = application.getJdText();
        if (jdText == null || jdText.isBlank()) {
            throw new AiException("この応募には求人票が登録されていません。応募の編集画面で求人票を貼り付けてください。");
        }
        String prompt = buildPrompt(jdText, language);
        String json = aiCache.getOrGenerate(application, AiResultType.JD_SUMMARY, prompt);
        return parse(json);
    }

    /**
     * プロンプトの考え方：
     * - 読み手は日本で就活する留学生 → 言語ごとの書き方を SummaryLanguage で指示
     * - 求人票にないことは書かせない（作り話の防止）、分からない項目は空のリスト
     * - JSONの形を例で示す（「JSON」という単語は Groq の JSON モードの条件）
     */
    public String buildPrompt(String jdText, SummaryLanguage language) {
        return """
                あなたは日本で就職活動をしている外国人留学生を支援するアシスタントです。
                次の求人票を読み、%s で分かりやすく説明してください。

                ルール：
                - summary、work、skills、conditions は、求人票に書かれている内容だけを使ってください。書かれていないことを推測で書かないでください。
                - prepare は、求人票の内容をもとに、応募者が面接までに準備するとよいことを書いてください。
                - 書かれていない項目は空のリスト [] にしてください。
                - リストの各項目は短い1文にしてください。各リストは8項目までにしてください。

                回答は次の形式の JSON だけを返してください。説明文は不要です。
                {"summary":"どんな仕事か2〜3文で","work":["主な仕事内容"],"skills":["必要なスキル・歓迎スキル"],"conditions":["勤務地・給与・勤務時間などの条件"],"prepare":["面接までに準備するとよいこと"]}

                求人票：
                %s
                """.formatted(language.getInstruction(), jdText);
    }

    /**
     * AIの回答（JSON）を JdSummary に変換する。
     * - JSONとして読めない → AiException
     * - 各リスト：null は空のリストに、空白だけの項目は捨てる、前後の空白は trim、最大 MAX_ITEMS 件
     * - summary もリストも全部空 → AiException（解説として使えない）
     */
    public JdSummary parse(String json) {
        SummaryJson root;
        try {
            root = jsonMapper.readValue(json, SummaryJson.class);
        } catch (JacksonException e) {
            throw new AiException("AIの回答を読み取れませんでした。もう一度お試しください。", e);
        }
        if (root == null) {
            throw new AiException("AIの回答を読み取れませんでした。もう一度お試しください。");
        }

        String summary = root.summary() == null ? "" : root.summary().trim();
        JdSummary result = new JdSummary(summary,
                clean(root.work()), clean(root.skills()), clean(root.conditions()), clean(root.prepare()));

        if (summary.isEmpty() && result.work().isEmpty() && result.skills().isEmpty()
                && result.conditions().isEmpty() && result.prepare().isEmpty()) {
            throw new AiException("求人票から解説を作れませんでした。求人票の内容を確認してください。");
        }
        return result;
    }

    // null・空白を取り除き、trim して、上限までにする
    private List<String> clean(List<String> items) {
        List<String> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            result.add(item.trim());
            if (result.size() >= MAX_ITEMS) {
                break;
            }
        }
        return result;
    }

    // ---- AIが返すJSONの形 ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SummaryJson(String summary, List<String> work, List<String> skills,
                       List<String> conditions, List<String> prepare) {}
}
