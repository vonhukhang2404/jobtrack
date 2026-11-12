package com.khang.jobtrack.service;

import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.AiClient;
import com.khang.jobtrack.ai.TaskSuggestion;
import com.khang.jobtrack.entity.AiResult;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.AiResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * buildPrompt / parse / キャッシュのテスト（本物のAIは呼ばない）。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskServiceTest {

    @Mock
    ApplicationService applicationService;

    @Mock
    TaskService taskService;

    @Mock
    AiClient aiClient;

    @Mock
    AiResultRepository aiResultRepository;

    @InjectMocks
    AiTaskService aiTaskService;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    // ---------- buildPrompt ----------

    @Test
    @DisplayName("buildPrompt: 求人票・今日の日付・JSONの形・「JSON」という単語を含む")
    void buildPrompt_containsRequiredParts() {
        String prompt = aiTaskService.buildPrompt("ES提出締切：11月20日", TODAY);

        assertThat(prompt).contains("ES提出締切：11月20日");
        assertThat(prompt).contains("2026-11-09");
        assertThat(prompt).contains("\"tasks\"");
        assertThat(prompt).contains("dueDate");
        assertThat(prompt).contains("JSON"); // Groq の JSON モードの条件
    }

    // ---------- parse ----------

    @Test
    @DisplayName("parse: 正しいJSON → 候補2件")
    void parse_valid() {
        String json = """
                {"tasks":[
                  {"title":"ES提出","dueDate":"2026-11-20"},
                  {"title":"SPI受検","dueDate":"2026-11-25"}
                ]}""";

        List<TaskSuggestion> result = aiTaskService.parse(json, TODAY);

        assertThat(result).containsExactly(
                new TaskSuggestion("ES提出", LocalDate.of(2026, 11, 20)),
                new TaskSuggestion("SPI受検", LocalDate.of(2026, 11, 25)));
    }

    @Test
    @DisplayName("parse: 過去の締切は捨てる（今日はOK）")
    void parse_skipsPastDates() {
        String json = """
                {"tasks":[
                  {"title":"説明会予約","dueDate":"2026-11-01"},
                  {"title":"ES提出","dueDate":"2026-11-09"}
                ]}""";

        assertThat(aiTaskService.parse(json, TODAY))
                .containsExactly(new TaskSuggestion("ES提出", LocalDate.of(2026, 11, 9)));
    }

    @Test
    @DisplayName("parse: 日付として読めないものは捨てる")
    void parse_skipsInvalidDates() {
        String json = """
                {"tasks":[
                  {"title":"A","dueDate":"来週金曜"},
                  {"title":"B","dueDate":"2026-13-40"},
                  {"title":"C","dueDate":null},
                  {"title":"D","dueDate":"2026-12-01"}
                ]}""";

        assertThat(aiTaskService.parse(json, TODAY))
                .containsExactly(new TaskSuggestion("D", LocalDate.of(2026, 12, 1)));
    }

    @Test
    @DisplayName("parse: タイトルが空なら捨て、前後の空白は取り除く")
    void parse_trimsAndSkipsBlankTitles() {
        String json = """
                {"tasks":[
                  {"title":"   ","dueDate":"2026-11-20"},
                  {"title":null,"dueDate":"2026-11-21"},
                  {"title":"  ES提出  ","dueDate":"2026-11-22"}
                ]}""";

        assertThat(aiTaskService.parse(json, TODAY))
                .containsExactly(new TaskSuggestion("ES提出", LocalDate.of(2026, 11, 22)));
    }

    @Test
    @DisplayName("parse: tasks が空・存在しない → 空のリスト")
    void parse_emptyOrMissing() {
        assertThat(aiTaskService.parse("{\"tasks\":[]}", TODAY)).isEmpty();
        assertThat(aiTaskService.parse("{}", TODAY)).isEmpty();
    }

    @Test
    @DisplayName("parse: 最大10件まで")
    void parse_limitsToMax() {
        StringBuilder sb = new StringBuilder("{\"tasks\":[");
        for (int i = 1; i <= 15; i++) {
            if (i > 1) sb.append(',');
            sb.append("{\"title\":\"T").append(i).append("\",\"dueDate\":\"2026-12-01\"}");
        }
        sb.append("]}");

        assertThat(aiTaskService.parse(sb.toString(), TODAY)).hasSize(AiTaskService.MAX_SUGGESTIONS);
    }

    @Test
    @DisplayName("parse: JSONとして読めない → AiException")
    void parse_brokenJson() {
        assertThatThrownBy(() -> aiTaskService.parse("締切は11月20日です", TODAY))
                .isInstanceOf(AiException.class);
    }

    // ---------- suggest（キャッシュ） ----------

    private Application applicationWithJd(String jdText) {
        Application application = new Application();
        application.setJdText(jdText);
        return application;
    }

    @Test
    @DisplayName("suggest: 同じ内容の結果が保存されていれば、AIを呼ばずに再利用する")
    void suggest_usesCache() {
        User user = new User();
        when(applicationService.findOwned(user, 1L)).thenReturn(applicationWithJd("ES締切：11月20日"));
        AiResult cached = new AiResult();
        cached.setOutputText("{\"tasks\":[{\"title\":\"ES提出\",\"dueDate\":\"2026-11-20\"}]}");
        when(aiResultRepository.findFirstByApplicationIdAndTypeAndInputTextOrderByIdDesc(any(), any(), any()))
                .thenReturn(Optional.of(cached));

        List<TaskSuggestion> result = aiTaskService.suggest(user, 1L, TODAY);

        assertThat(result).containsExactly(new TaskSuggestion("ES提出", LocalDate.of(2026, 11, 20)));
        verify(aiClient, never()).generateJson(any());
        verify(aiResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("suggest: 保存された結果がなければAIを呼び、結果を保存する")
    void suggest_callsAiAndSaves() {
        User user = new User();
        when(applicationService.findOwned(user, 1L)).thenReturn(applicationWithJd("ES締切：11月20日"));
        when(aiResultRepository.findFirstByApplicationIdAndTypeAndInputTextOrderByIdDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(aiClient.generateJson(any())).thenReturn("{\"tasks\":[]}");

        assertThat(aiTaskService.suggest(user, 1L, TODAY)).isEmpty();

        verify(aiClient).generateJson(any());
        verify(aiResultRepository).save(any(AiResult.class));
    }

    @Test
    @DisplayName("suggest: 求人票が未登録 → AiException（AIは呼ばない）")
    void suggest_noJobDescription() {
        User user = new User();
        when(applicationService.findOwned(user, 1L)).thenReturn(applicationWithJd("  "));

        assertThatThrownBy(() -> aiTaskService.suggest(user, 1L, TODAY))
                .isInstanceOf(AiException.class);
        verify(aiClient, never()).generateJson(any());
    }
}
