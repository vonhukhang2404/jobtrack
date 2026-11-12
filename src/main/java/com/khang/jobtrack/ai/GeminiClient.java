package com.khang.jobtrack.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Gemini API（generateContent）を呼び出すクラス。
 * プロンプトを渡すと、AIが返したテキスト（JSON文字列）をそのまま返す。
 * 「どう聞くか（プロンプト）」「返ってきたJSONをどう読むか」は AiTaskService の担当。
 * ai.provider=gemini のときだけ使われる（初期設定は Groq）。
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
public class GeminiClient implements AiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiClient(@Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model:gemini-2.5-flash}") String model) {
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .build();
    }

    /**
     * @param prompt AIへの指示文
     * @return AIの回答テキスト（JSON形式を指定しているので JSON 文字列）
     */
    @Override
    public String generateJson(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiException("AIのAPIキーが設定されていません（環境変数 GEMINI_API_KEY）");
        }

        // リクエスト本文：{"contents":[{"parts":[{"text": prompt}]}], "generationConfig": {...}}
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json", // JSONだけを返させる
                        "temperature", 0                         // 毎回なるべく同じ答えにする
                )
        );

        GeminiResponse response;
        try {
            response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey) // キーはURLではなくヘッダーで送る（ログに残りにくい）
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new AiException("AIの利用回数の上限に達しました。しばらくしてから再度お試しください。", e);
        } catch (HttpClientErrorException e) {
            throw new AiException("AIへのリクエストが拒否されました（" + e.getStatusCode().value()
                    + "）。APIキーとモデル名を確認してください。", e);
        } catch (RestClientException e) {
            throw new AiException("AIに接続できませんでした。時間をおいて再度お試しください。", e);
        }

        // candidates[0].content.parts[0].text を取り出す
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new AiException("AIから回答がありませんでした。");
        }
        Content content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new AiException("AIから回答がありませんでした。");
        }
        return content.parts().get(0).text();
    }

    // ---- Gemini の応答JSONのうち、必要な部分だけを受け取る record ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {}
}
