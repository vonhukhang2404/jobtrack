package com.khang.jobtrack.service;

import com.khang.jobtrack.ai.AiClient;
import com.khang.jobtrack.entity.AiResult;
import com.khang.jobtrack.entity.AiResultType;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.repository.AiResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AIの結果の再利用（キャッシュ）のテスト。
 */
@ExtendWith(MockitoExtension.class)
class AiResultCacheServiceTest {

    @Mock
    AiClient aiClient;

    @Mock
    AiResultRepository aiResultRepository;

    @InjectMocks
    AiResultCacheService aiCache;

    @Test
    @DisplayName("保存された結果があれば、AIを呼ばずに再利用する")
    void returnsCachedResult() {
        Application application = new Application();
        AiResult cached = new AiResult();
        cached.setOutputText("{\"tasks\":[]}");
        when(aiResultRepository.findFirstByApplicationIdAndTypeAndInputTextOrderByIdDesc(
                any(), eq(AiResultType.AUTO_FILL), eq("PROMPT")))
                .thenReturn(Optional.of(cached));

        String result = aiCache.getOrGenerate(application, AiResultType.AUTO_FILL, "PROMPT");

        assertThat(result).isEqualTo("{\"tasks\":[]}");
        verify(aiClient, never()).generateJson(any());
        verify(aiResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("保存された結果がなければAIを呼び、種類・入力・出力を保存する")
    void callsAiAndSaves() {
        Application application = new Application();
        when(aiResultRepository.findFirstByApplicationIdAndTypeAndInputTextOrderByIdDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(aiClient.generateJson("PROMPT")).thenReturn("{\"questions\":[]}");

        String result = aiCache.getOrGenerate(application, AiResultType.QUESTIONS, "PROMPT");

        assertThat(result).isEqualTo("{\"questions\":[]}");
        ArgumentCaptor<AiResult> saved = ArgumentCaptor.forClass(AiResult.class);
        verify(aiResultRepository).save(saved.capture());
        assertThat(saved.getValue().getApplication()).isSameAs(application);
        assertThat(saved.getValue().getType()).isEqualTo(AiResultType.QUESTIONS);
        assertThat(saved.getValue().getInputText()).isEqualTo("PROMPT");
        assertThat(saved.getValue().getOutputText()).isEqualTo("{\"questions\":[]}");
    }
}
