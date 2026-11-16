package com.khang.jobtrack.service;

import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.AnswerReview;
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
 * 面接の回答の添削のテスト（本物のAIは呼ばない）。
 */
@ExtendWith(MockitoExtension.class)
class AnswerReviewServiceTest {

    @Mock
    ApplicationService applicationService;

    @Mock
    AiResultCacheService aiCache;

    @InjectMocks
    AnswerReviewService service;

    private Application application() {
        Application application = new Application();
        application.setPosition("Javaエンジニア");
        return application;
    }

    // ---------- buildPrompt ----------

    @Test
    @DisplayName("buildPrompt: 質問・意図・回答・JSONの形と、経験を作らないルールを含む")
    void buildPrompt_containsRequiredParts() {
        String prompt = service.buildPrompt("Javaエンジニア", "志望動機を教えてください。", "熱意", "御社のサービスが好きです。");

        assertThat(prompt).contains("Javaエンジニア", "志望動機を教えてください。", "熱意", "御社のサービスが好きです。");
        assertThat(prompt).contains("JSON", "\"revised\"", "足してはいけません", "【ここに苦労をどう解決したか】");
    }

    // ---------- parse ----------

    @Test
    @DisplayName("parse: 正しいJSON → 良い点・改善点・修正例を読み取る")
    void parse_valid() {
        String json = """
                {"goodPoints":[" 結論から話している "],"improvements":["具体例を入れる",""],
                 "revised":" 私が御社を志望する理由は【ここに具体例】です。 "}""";

        AnswerReview result = service.parse(json);

        assertThat(result.goodPoints()).containsExactly("結論から話している");
        assertThat(result.improvements()).containsExactly("具体例を入れる");
        assertThat(result.revised()).isEqualTo("私が御社を志望する理由は【ここに具体例】です。");
    }

    @Test
    @DisplayName("parse: 全部空・JSONが壊れている → AiException")
    void parse_emptyOrBroken() {
        assertThatThrownBy(() -> service.parse("{\"goodPoints\":[],\"revised\":\" \"}")).isInstanceOf(AiException.class);
        assertThatThrownBy(() -> service.parse("良い回答です")).isInstanceOf(AiException.class);
    }

    // ---------- review ----------

    @Test
    @DisplayName("review: ES_REVIEW としてキャッシュ経由でAIを呼ぶ")
    void review_usesCacheWithEsReview() {
        User user = new User();
        Application application = application();
        when(applicationService.findOwned(user, 1L)).thenReturn(application);
        when(aiCache.getOrGenerate(eq(application), eq(AiResultType.ES_REVIEW), any()))
                .thenReturn("{\"revised\":\"修正例です。\"}");

        AnswerReview result = service.review(user, 1L, "志望動機は？", null, "御社が好きです。");

        assertThat(result.revised()).isEqualTo("修正例です。");
    }

    @Test
    @DisplayName("review: 回答が空・長すぎる → AiException（AIは呼ばない）")
    void review_invalidAnswer() {
        User user = new User();
        when(applicationService.findOwned(user, 1L)).thenReturn(application());

        assertThatThrownBy(() -> service.review(user, 1L, "志望動機は？", "", "  "))
                .isInstanceOf(AiException.class);
        assertThatThrownBy(() -> service.review(user, 1L, "志望動機は？", "",
                "あ".repeat(AnswerReviewService.MAX_ANSWER_LENGTH + 1)))
                .isInstanceOf(AiException.class);
        verify(aiCache, never()).getOrGenerate(any(), any(), any());
    }
}
