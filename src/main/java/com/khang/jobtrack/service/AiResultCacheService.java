package com.khang.jobtrack.service;

import com.khang.jobtrack.ai.AiClient;
import com.khang.jobtrack.entity.AiResult;
import com.khang.jobtrack.entity.AiResultType;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.repository.AiResultRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * AIの呼び出しと結果の再利用（キャッシュ）をまとめたクラス。
 * 締切抽出・求人票の解説・想定質問の3つの機能で共通して使う。
 */
@Service
public class AiResultCacheService {

    private final AiClient aiClient;
    private final AiResultRepository aiResultRepository;

    public AiResultCacheService(AiClient aiClient, AiResultRepository aiResultRepository) {
        this.aiClient = aiClient;
        this.aiResultRepository = aiResultRepository;
    }

    /**
     * 同じ応募・同じ種類・同じプロンプトで前にAIを呼んでいれば、保存した結果（ai_results）を返す。
     * なければAIを呼び、結果を保存してから返す。
     */
    public String getOrGenerate(Application application, AiResultType type, String prompt) {
        Optional<AiResult> cached = aiResultRepository.findFirstByApplicationIdAndTypeAndInputTextOrderByIdDesc(
                application.getId(), type, prompt);
        if (cached.isPresent()) {
            return cached.get().getOutputText();
        }

        String json = aiClient.generateJson(prompt);

        AiResult aiResult = new AiResult();
        aiResult.setApplication(application);
        aiResult.setType(type);
        aiResult.setInputText(prompt);
        aiResult.setOutputText(json);
        aiResultRepository.save(aiResult);

        return json;
    }
}
