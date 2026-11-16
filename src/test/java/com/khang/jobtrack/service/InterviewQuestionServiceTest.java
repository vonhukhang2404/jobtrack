package com.khang.jobtrack.service;

import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.InterviewQuestion;
import com.khang.jobtrack.entity.AiResultType;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.Interview;
import com.khang.jobtrack.entity.InterviewFormat;
import com.khang.jobtrack.entity.InterviewResult;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.InterviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 面接の想定質問のテスト（本物のAIは呼ばない）。
 */
@ExtendWith(MockitoExtension.class)
class InterviewQuestionServiceTest {

    @Mock
    ApplicationService applicationService;

    @Mock
    InterviewRepository interviewRepository;

    @Mock
    AiResultCacheService aiCache;

    @InjectMocks
    InterviewQuestionService service;

    private static Interview interview(int round, String notes) {
        Interview i = new Interview();
        i.setRound(round);
        i.setScheduledAt(LocalDateTime.of(2026, 10, 6, 14, 0));
        i.setFormat(InterviewFormat.ONLINE);
        i.setResult(InterviewResult.PASSED);
        i.setNotes(notes);
        return i;
    }

    // ---------- buildPrompt ----------

    @Test
    @DisplayName("buildPrompt: 職種・求人票・面接メモ・JSONの形を含む")
    void buildPrompt_containsRequiredParts() {
        String prompt = service.buildPrompt("Javaエンジニア", "Spring Bootでの開発",
                List.of(interview(1, "志望動機を深掘りされた")), List.of());

        assertThat(prompt).contains("Javaエンジニア", "Spring Bootでの開発", "JSON", "\"questions\"");
        assertThat(prompt).contains("第1次（2026-10-06・オンライン・通過）：志望動機を深掘りされた");
        assertThat(prompt).contains("新卒"); // 実務経験を前提にしない
        assertThat(prompt).contains("この企業").doesNotContain("IT企業"); // IT以外の会社・職種にも対応
        assertThat(prompt).doesNotContain("違う質問"); // 普通に作るとき
    }

    @Test
    @DisplayName("buildPrompt: 求人票も面接もない部分は「ない」と書く")
    void buildPrompt_whenMissing() {
        String prompt = service.buildPrompt("Javaエンジニア", null, List.of(), List.of());

        assertThat(prompt).contains("（登録されていません）", "（まだありません）");
    }

    // ---------- parse ----------

    @Test
    @DisplayName("parse: 正しいJSON → 質問を読み取る。intent/hint が null なら空文字")
    void parse_valid() {
        String json = """
                {"questions":[
                  {"question":" 志望動機を教えてください。 ","intent":"熱意","hint":"会社を選んだ理由を具体的に"},
                  {"question":"チーム開発の経験は？","intent":null,"hint":null}
                ]}""";

        List<InterviewQuestion> result = service.parse(json);

        assertThat(result).containsExactly(
                new InterviewQuestion("志望動機を教えてください。", "熱意", "会社を選んだ理由を具体的に"),
                new InterviewQuestion("チーム開発の経験は？", "", ""));
    }

    @Test
    @DisplayName("parse: 質問が空なら捨て、最大5件")
    void parse_skipsBlankAndLimits() {
        StringBuilder sb = new StringBuilder("{\"questions\":[{\"question\":\"  \"}");
        for (int i = 1; i <= 8; i++) {
            sb.append(",{\"question\":\"Q").append(i).append("\"}");
        }
        sb.append("]}");

        List<InterviewQuestion> result = service.parse(sb.toString());

        assertThat(result).hasSize(InterviewQuestionService.MAX_QUESTIONS);
        assertThat(result.get(0).question()).isEqualTo("Q1");
    }

    @Test
    @DisplayName("parse: 質問が1つもない・JSONが壊れている → AiException")
    void parse_emptyOrBroken() {
        assertThatThrownBy(() -> service.parse("{\"questions\":[]}")).isInstanceOf(AiException.class);
        assertThatThrownBy(() -> service.parse("質問1：志望動機")).isInstanceOf(AiException.class);
    }

    // ---------- generate ----------

    @Test
    @DisplayName("generate: QUESTIONS としてキャッシュ経由でAIを呼ぶ（面接メモだけでもOK）")
    void generate_usesCacheWithQuestions() {
        User user = new User();
        Application application = new Application();
        application.setPosition("Javaエンジニア");
        when(applicationService.findOwned(user, 1L)).thenReturn(application);
        when(interviewRepository.findByApplicationIdOrderByRoundAscIdAsc(1L))
                .thenReturn(List.of(interview(1, "OOPについて聞かれた")));
        when(aiCache.getOrGenerate(eq(application), eq(AiResultType.QUESTIONS), any()))
                .thenReturn("{\"questions\":[{\"question\":\"継承とは？\"}]}");

        assertThat(service.generate(user, 1L, List.of())).extracting(InterviewQuestion::question).containsExactly("継承とは？");
    }

    @Test
    @DisplayName("generate: 求人票も面接メモもない → AiException（AIは呼ばない）")
    void generate_nothingToUse() {
        User user = new User();
        when(applicationService.findOwned(user, 1L)).thenReturn(new Application());
        when(interviewRepository.findByApplicationIdOrderByRoundAscIdAsc(1L))
                .thenReturn(List.of(interview(1, " ")));

        assertThatThrownBy(() -> service.generate(user, 1L, List.of())).isInstanceOf(AiException.class);
        verify(aiCache, never()).getOrGenerate(any(), any(), any());
    }

    // ---------- 別の質問を作る ----------

    @Test
    @DisplayName("buildPrompt: 前に出た質問を渡すと「違う質問」を指示し、プロンプトが変わる（キャッシュも別になる）")
    void buildPrompt_withExcluded() {
        String normal = service.buildPrompt("インフラエンジニア", "AWSの運用", List.of(), List.of());
        String again = service.buildPrompt("インフラエンジニア", "AWSの運用", List.of(), List.of("志望動機を教えてください。"));

        assertThat(again).contains("違う質問", "- 志望動機を教えてください。");
        assertThat(again).isNotEqualTo(normal);
    }

    @Test
    @DisplayName("cleanExcluded: 空白・重複を除き、200文字で切り、新しい方から20件まで")
    void cleanExcluded() {
        java.util.List<String> input = new java.util.ArrayList<>();
        input.add("  ");
        input.add(null);
        input.add(" Q0 ");
        input.add("Q0");
        input.add("あ".repeat(250));
        for (int i = 1; i <= 25; i++) {
            input.add("Q" + i);
        }

        List<String> result = InterviewQuestionService.cleanExcluded(input);

        assertThat(result).hasSize(InterviewQuestionService.MAX_EXCLUDED);
        assertThat(result.get(result.size() - 1)).isEqualTo("Q25");
        assertThat(result).doesNotContain("Q0"); // 古いものから捨てる
        assertThat(InterviewQuestionService.cleanExcluded(List.of("あ".repeat(250))).get(0)).hasSize(200);
        assertThat(InterviewQuestionService.cleanExcluded(null)).isEmpty();
    }
}
