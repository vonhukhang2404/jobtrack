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
 * Groq API（OpenAI互換の Chat Completions）を呼び出す。
 * ai.provider=groq のとき（初期設定）に使われる。
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "groq", matchIfMissing = true)
public class GroqClient implements AiClient {

    private static final String BASE_URL = "https://api.groq.com/openai/v1";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GroqClient(@Value("${groq.api-key:}") String apiKey,
                      @Value("${groq.model}") String model) {
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

    @Override
    public String generateJson(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiException("AIのAPIキーが設定されていません（環境変数 GROQ_API_KEY）");
        }

        // {"model":..., "messages":[{"role":"user","content":prompt}], "temperature":0,
        //  "response_format":{"type":"json_object"}}
        // ※ JSONモードでは、プロンプトに「JSON」という単語が必要
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0,
                "response_format", Map.of("type", "json_object")
        );

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new AiException("AIの利用回数の上限に達しました。しばらくしてから再度お試しください。", e);
        } catch (HttpClientErrorException e) {
            throw new AiException("AIへのリクエストが拒否されました（" + e.getStatusCode().value()
                    + "）。APIキーとモデル名を確認してください。", e);
        } catch (RestClientException e) {
            throw new AiException("AIに接続できませんでした。時間をおいて再度お試しください。", e);
        }

        // choices[0].message.content を取り出す
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw new AiException("AIから回答がありませんでした。");
        }
        return response.choices().get(0).message().content();
    }

    // ---- 応答JSONのうち、必要な部分だけを受け取る record ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {}
}
