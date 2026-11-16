package com.khang.jobtrack.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.InterviewQuestion;
import com.khang.jobtrack.entity.AiResultType;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.Interview;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.InterviewRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 求人票と、その応募のこれまでの面接メモから、次の面接の想定質問をAIで作る。
 * 流れ：buildPrompt → (キャッシュ or AI) → parse → 画面に表示
 */
@Service
public class InterviewQuestionService {

    /** 1回で作る質問の上限 */
    public static final int MAX_QUESTIONS = 5;

    /** 「別の質問を作る」でプロンプトに入れる、前に出た質問の上限（プロンプトが長くなりすぎないように） */
    public static final int MAX_EXCLUDED = 20;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ApplicationService applicationService;
    private final InterviewRepository interviewRepository;
    private final AiResultCacheService aiCache;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public InterviewQuestionService(ApplicationService applicationService,
                                    InterviewRepository interviewRepository,
                                    AiResultCacheService aiCache) {
        this.applicationService = applicationService;
        this.interviewRepository = interviewRepository;
        this.aiCache = aiCache;
    }

    /** 画面に「これまでの面接」を表示するため */
    public List<Interview> findInterviews(User user, Long applicationId) {
        applicationService.findOwned(user, applicationId); // 他人の応募なら404
        return interviewRepository.findByApplicationIdOrderByRoundAscIdAsc(applicationId);
    }

    /**
     * @param excluded 前に出た質問（「別の質問を作る」のとき）。空なら普通に作る
     */
    public List<InterviewQuestion> generate(User user, Long applicationId, List<String> excluded) {
        Application application = applicationService.findOwned(user, applicationId); // 他人の応募なら404
        List<Interview> interviews = interviewRepository.findByApplicationIdOrderByRoundAscIdAsc(applicationId);

        String jdText = application.getJdText();
        boolean hasJd = jdText != null && !jdText.isBlank();
        boolean hasNotes = interviews.stream().anyMatch(i -> i.getNotes() != null && !i.getNotes().isBlank());
        if (!hasJd && !hasNotes) {
            throw new AiException("求人票か面接のメモを登録してください。どちらもないと質問を作れません。");
        }

        String prompt = buildPrompt(application.getPosition(), jdText, interviews, excluded);
        String json = aiCache.getOrGenerate(application, AiResultType.QUESTIONS, prompt);
        return parse(json);
    }

    /**
     * プロンプトの考え方：
     * - 面接官の立場で、職種・求人票・これまでの面接メモから「次に聞かれそうな質問」を作らせる
     * - 前の面接の深掘りと、まだ聞かれていないテーマのバランスを指示
     * - 質問は実際の面接と同じ丁寧な日本語、答え方のヒントは留学生向けにやさしい日本語
     * - 応募者は新卒の学生 → 「実務経験」を前提にした質問をさせない（動作確認で見つけた改善点）
     * - 職種はIT以外の会社のIT部門やインフラ職もあるので「IT企業」と決めつけず「この企業」とする
     * - excluded があれば「これとは違う質問」を指示 → プロンプトが変わるので、キャッシュも別になり新しい質問が出る
     */
    public String buildPrompt(String position, String jdText, List<Interview> interviews, List<String> excluded) {
        String jd = (jdText == null || jdText.isBlank()) ? "（登録されていません）" : jdText;
        return """
                あなたはこの企業の採用面接官です。
                次の職種・求人票・これまでの面接の記録をもとに、応募者が次の面接で聞かれそうな質問を5つ作ってください。

                ルール：
                - 質問は、実際の面接で使う丁寧な日本語にしてください。
                - これまでの面接で話したことの深掘りと、まだ聞かれていない大事なテーマ（志望動機、技術、チーム開発、入社後にやりたいこと など）をバランスよく入れてください。
                - intent は、その質問で面接官が確認したいことを1文で書いてください。
                - hint は、答えるときのポイントを、やさしい日本語で1〜2文で書いてください。
                - 応募者は新卒の学生（日本で就職活動をしている留学生）です。実務経験があることを前提にした質問はせず、学校の授業・個人開発・チーム開発・アルバイトの経験を聞く質問にしてください。
                - 求人票や面接の記録にない事実（応募者の経歴など）を決めつけないでください。

                回答は次の形式の JSON だけを返してください。説明文は不要です。
                {"questions":[{"question":"質問","intent":"確認したいこと","hint":"答えるときのポイント"}]}

                職種：%s

                求人票：
                %s

                これまでの面接の記録：
                %s
                """.formatted(position, jd, formatInterviews(interviews)) + formatExcluded(excluded);
    }

    // 「別の質問を作る」：前に出た質問を並べて、違う質問を作らせる
    String formatExcluded(List<String> excluded) {
        List<String> list = cleanExcluded(excluded);
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n次の質問はすでに練習したので、これらとは違う質問を作ってください：\n");
        for (String q : list) {
            sb.append("- ").append(q).append('\n');
        }
        return sb.toString();
    }

    /** 空白を除き、長すぎるものを切り、重複を除いて、新しい方から MAX_EXCLUDED 件にする（画面から送られる値なので） */
    public static List<String> cleanExcluded(List<String> excluded) {
        List<String> result = new ArrayList<>();
        if (excluded == null) {
            return result;
        }
        for (String q : excluded) {
            if (q == null || q.isBlank()) {
                continue;
            }
            String t = q.trim();
            if (t.length() > 200) {
                t = t.substring(0, 200);
            }
            if (!result.contains(t)) {
                result.add(t);
            }
        }
        if (result.size() > MAX_EXCLUDED) {
            result = new ArrayList<>(result.subList(result.size() - MAX_EXCLUDED, result.size()));
        }
        return result;
    }

    // 例：「第1次（2026-10-06・オンライン・通過）：志望動機を深掘りされた」
    String formatInterviews(List<Interview> interviews) {
        if (interviews == null || interviews.isEmpty()) {
            return "（まだありません）";
        }
        StringBuilder sb = new StringBuilder();
        for (Interview i : interviews) {
            sb.append("- 第").append(i.getRound()).append("次（")
                    .append(i.getScheduledAt() == null ? "日付不明" : i.getScheduledAt().format(DATE)).append("・")
                    .append(i.getFormat() == null ? "" : i.getFormat().getLabel()).append("・")
                    .append(i.getResult() == null ? "" : i.getResult().getLabel()).append("）：")
                    .append(i.getNotes() == null || i.getNotes().isBlank() ? "メモなし" : i.getNotes().trim())
                    .append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * AIの回答（JSON）を想定質問のリストに変換する。
     * - JSONとして読めない → AiException
     * - question が空 → 捨てる。intent / hint が null → 空文字。前後の空白は trim
     * - 最大 MAX_QUESTIONS 件。1件もない → AiException
     */
    public List<InterviewQuestion> parse(String json) {
        QuestionsJson root;
        try {
            root = jsonMapper.readValue(json, QuestionsJson.class);
        } catch (JacksonException e) {
            throw new AiException("AIの回答を読み取れませんでした。もう一度お試しください。", e);
        }

        List<InterviewQuestion> result = new ArrayList<>();
        if (root != null && root.questions() != null) {
            for (QuestionItem item : root.questions()) {
                if (item == null || item.question() == null || item.question().isBlank()) {
                    continue;
                }
                result.add(new InterviewQuestion(item.question().trim(), trimOrEmpty(item.intent()),
                        trimOrEmpty(item.hint())));
                if (result.size() >= MAX_QUESTIONS) {
                    break;
                }
            }
        }
        if (result.isEmpty()) {
            throw new AiException("想定質問を作れませんでした。もう一度お試しください。");
        }
        return result;
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    // ---- AIが返すJSONの形 ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuestionsJson(List<QuestionItem> questions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuestionItem(String question, String intent, String hint) {}
}
