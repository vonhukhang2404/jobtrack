package com.khang.jobtrack.service;

import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.JdSummary;
import com.khang.jobtrack.ai.SummaryLanguage;
import com.khang.jobtrack.entity.AiResultType;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 求人票の解説のテスト（本物のAIは呼ばない）。
 */
@ExtendWith(MockitoExtension.class)
class JdSummaryServiceTest {

    @Mock
    ApplicationService applicationService;

    @Mock
    AiResultCacheService aiCache;

    @InjectMocks
    JdSummaryService jdSummaryService;

    // ---------- buildPrompt ----------

    @Test
    @DisplayName("buildPrompt: 求人票・言語の指示・JSONの形を含み、言語ごとに内容が変わる")
    void buildPrompt_containsRequiredParts() {
        String easy = jdSummaryService.buildPrompt("Java開発（Spring Boot）", SummaryLanguage.EASY_JA);
        String vi = jdSummaryService.buildPrompt("Java開発（Spring Boot）", SummaryLanguage.VI);

        assertThat(easy).contains("Java開発（Spring Boot）", "やさしい日本語", "JSON", "\"summary\"", "\"prepare\"");
        assertThat(vi).contains("ベトナム語");
        // 言語が違えばプロンプトも違う → キャッシュも別になる
        assertThat(easy).isNotEqualTo(vi);
    }

    // ---------- parse ----------

    @Test
    @DisplayName("parse: 正しいJSON → すべての項目を読み取る")
    void parse_valid() {
        String json = """
                {"summary":"Javaでシステムを作る仕事です。",
                 "work":["Webアプリの開発"],
                 "skills":["Java","SQL"],
                 "conditions":["大阪勤務"],
                 "prepare":["Spring Bootの経験を説明できるようにする"]}""";

        JdSummary result = jdSummaryService.parse(json);

        assertThat(result.summary()).isEqualTo("Javaでシステムを作る仕事です。");
        assertThat(result.work()).containsExactly("Webアプリの開発");
        assertThat(result.skills()).containsExactly("Java", "SQL");
        assertThat(result.conditions()).containsExactly("大阪勤務");
        assertThat(result.prepare()).containsExactly("Spring Bootの経験を説明できるようにする");
    }

    @Test
    @DisplayName("parse: 空白だけ・null の項目を捨て、前後の空白を取り除く。ないリストは空")
    void parse_cleansItems() {
        String json = """
                {"summary":"  概要  ","work":["  開発  ","  ",null],"skills":null}""";

        JdSummary result = jdSummaryService.parse(json);

        assertThat(result.summary()).isEqualTo("概要");
        assertThat(result.work()).containsExactly("開発");
        assertThat(result.skills()).isEmpty();
        assertThat(result.conditions()).isEmpty();
        assertThat(result.prepare()).isEmpty();
    }

    @Test
    @DisplayName("parse: 各リストは最大8件")
    void parse_limitsItems() {
        String json = """
                {"summary":"概要","skills":["1","2","3","4","5","6","7","8","9","10"]}""";

        assertThat(jdSummaryService.parse(json).skills()).hasSize(JdSummaryService.MAX_ITEMS);
    }

    @Test
    @DisplayName("parse: 全部空 → AiException")
    void parse_allEmpty() {
        assertThatThrownBy(() -> jdSummaryService.parse("{\"summary\":\" \",\"work\":[]}"))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("parse: JSONとして読めない → AiException")
    void parse_brokenJson() {
        assertThatThrownBy(() -> jdSummaryService.parse("この仕事はJavaの開発です"))
                .isInstanceOf(AiException.class);
    }

    // ---------- explain ----------

    @Test
    @DisplayName("explain: JD_SUMMARY としてキャッシュ経由でAIを呼ぶ")
    void explain_usesCacheWithJdSummary() {
        User user = new User();
        Application application = new Application();
        application.setJdText("Java開発");
        when(applicationService.findOwned(user, 1L)).thenReturn(application);
        when(aiCache.getOrGenerate(eq(application), eq(AiResultType.JD_SUMMARY), any()))
                .thenReturn("{\"summary\":\"概要\"}");

        assertThat(jdSummaryService.explain(user, 1L, SummaryLanguage.VI).summary()).isEqualTo("概要");
    }

    @Test
    @DisplayName("explain: 求人票が未登録 → AiException（AIは呼ばない）")
    void explain_noJobDescription() {
        User user = new User();
        when(applicationService.findOwned(user, 1L)).thenReturn(new Application());

        assertThatThrownBy(() -> jdSummaryService.explain(user, 1L, SummaryLanguage.EASY_JA))
                .isInstanceOf(AiException.class);
        verify(aiCache, never()).getOrGenerate(any(), any(), any());
    }
}
