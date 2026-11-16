package com.khang.jobtrack.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.AnswerReview;
import com.khang.jobtrack.entity.AiResultType;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.User;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 利用者が自分で書いた面接の回答を、AIが添削する（良い点・改善点・修正例）。
 * 流れ：入力チェック → buildPrompt → (キャッシュ or AI) → parse → 画面に表示（保存はしない）
 */
@Service
public class AnswerReviewService {

    public static final int MAX_ANSWER_LENGTH = 1000;
    public static final int MAX_QUESTION_LENGTH = 300;
    public static final int MAX_ITEMS = 5;

    private final ApplicationService applicationService;
    private final AiResultCacheService aiCache;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AnswerReviewService(ApplicationService applicationService, AiResultCacheService aiCache) {
        this.applicationService = applicationService;
        this.aiCache = aiCache;
    }

    public AnswerReview review(User user, Long applicationId, String question, String intent, String answer) {
        Application application = applicationService.findOwned(user, applicationId); // 他人の応募なら404

        if (question == null || question.isBlank()) {
            throw new AiException("質問がありません。想定質問の画面からやり直してください。");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new AiException("質問が長すぎます。");
        }
        if (answer == null || answer.isBlank()) {
            throw new AiException("回答を入力してください。");
        }
        if (answer.length() > MAX_ANSWER_LENGTH) {
            throw new AiException("回答は" + MAX_ANSWER_LENGTH + "文字以内で入力してください。");
        }

        String prompt = buildPrompt(application.getPosition(), question.trim(),
                intent == null ? "" : intent.trim(), answer.trim());
        String json = aiCache.getOrGenerate(application, AiResultType.ES_REVIEW, prompt);
        return parse(json);
    }

    /**
     * プロンプトの考え方：
     * - 本人の経験・内容を活かす。書かれていない経験や数字を作らせない（面接で話せない嘘になるため）
     *   具体例が足りないところは【ここに苦労をどう解決したか】のように、本人が書き足す場所として示させる
     * - 動作確認で「ユーザーから評価をいただきました」など回答にない成果を足したので、
     *   「足してはいけないもの」を具体的に並べ、最後に自分で確認させるようにした
     * - 修正例は面接でそのまま話せる「です・ます」の日本語、1分程度（300字くらい）
     * - 良い点・改善点は留学生にも分かるやさしい日本語
     */
    public String buildPrompt(String position, String question, String intent, String answer) {
        return """
                あなたは日本の就職活動を支援するキャリアアドバイザーです。
                留学生の応募者が、面接の質問に対する回答を準備しました。添削してください。

                ルール（とても大事）：
                - revised（修正例）に書いてよい事実は、「応募者の回答」に書かれていることだけです。
                - 回答に書かれていない成果、評価、数字、使った技術、学んだこと、気持ちを新しく足してはいけません。
                  面接で深く聞かれたときに、本人が答えられなくなるからです。
                - 足りない内容は作らずに、【ここに苦労をどう解決したか】のように、本人が書き足す内容を【】で示してください。
                - goodPoints（良い点）は、回答に実際に書かれていることだけをほめてください。
                - improvements（改善点）は、何を足すとよいかを、やさしい日本語で書いてください。
                - goodPoints と improvements は、それぞれ短い文で5つまでにしてください。
                - revised は、面接でそのまま話せる丁寧な日本語（です・ます）で、300字以内にしてください。
                - 書き終わったら、revised に回答にない事実が入っていないか確認し、入っていたら【】に置き換えてください。

                回答は次の形式の JSON だけを返してください。説明文は不要です。
                {"goodPoints":["良い点"],"improvements":["改善点"],"revised":"修正例"}

                職種：%s
                質問：%s
                質問の意図：%s

                応募者の回答：
                %s
                """.formatted(position, question, intent.isEmpty() ? "（なし）" : intent, answer);
    }

    /**
     * AIの回答（JSON）を AnswerReview に変換する。
     * - JSONとして読めない → AiException
     * - 各リスト：null・空白の項目は捨てる、trim、最大 MAX_ITEMS 件
     * - revised が空で、良い点・改善点も空 → AiException
     */
    public AnswerReview parse(String json) {
        ReviewJson root;
        try {
            root = jsonMapper.readValue(json, ReviewJson.class);
        } catch (JacksonException e) {
            throw new AiException("AIの回答を読み取れませんでした。もう一度お試しください。", e);
        }
        if (root == null) {
            throw new AiException("AIの回答を読み取れませんでした。もう一度お試しください。");
        }

        String revised = root.revised() == null ? "" : root.revised().trim();
        AnswerReview result = new AnswerReview(clean(root.goodPoints()), clean(root.improvements()), revised);
        if (revised.isEmpty() && result.goodPoints().isEmpty() && result.improvements().isEmpty()) {
            throw new AiException("添削を作れませんでした。もう一度お試しください。");
        }
        return result;
    }

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
    record ReviewJson(List<String> goodPoints, List<String> improvements, String revised) {}
}
