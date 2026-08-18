package com.example.aiagentpublisher.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class DeepSeekGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekGateway.class);
    private static final int MAX_ATTEMPTS = 3;

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeepSeekGateway(@Value("${app.deepseek.api-key:}") String apiKey,
                           @Value("${app.deepseek.model}") String model,
                           @Qualifier("deepSeekRestClient") RestClient restClient) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = restClient;
    }

    @Override
    public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
        if (StringUtils.isBlank(apiKey)) {
            throw new IllegalStateException("DEEPSEEK_API_KEY is not set");
        }
        String content = chat(StringUtils.defaultString(systemPrompt) + jsonHint(responseType),
                StringUtils.defaultString(userPrompt));
        try {
            return objectMapper.readValue(unwrapJson(content), responseType);
        } catch (Exception e) {
            throw new IllegalStateException("DeepSeek returned JSON that does not match " + responseType.getSimpleName(), e);
        }
    }

    private String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 8192,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        RestClientException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String raw = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + apiKey)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return contentFromCompletion(raw);
            } catch (RestClientException e) {
                last = e;
                log.warn("DeepSeek attempt {}/{} failed", attempt, MAX_ATTEMPTS);
            }
        }
        throw new IllegalStateException("DeepSeek request failed after retries", last);
    }

    private String contentFromCompletion(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new IllegalStateException("DeepSeek returned an empty body");
        }
        try {
            JsonNode content = objectMapper.readTree(raw).path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || StringUtils.isBlank(content.asText())) {
                throw new IllegalStateException("DeepSeek returned no message content");
            }
            return content.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("DeepSeek returned an invalid chat completion", e);
        }
    }

    private static String unwrapJson(String content) {
        String trimmed = StringUtils.trim(content);
        if (StringUtils.startsWith(trimmed, "```")) {
            trimmed = StringUtils.removeStart(trimmed, "```json");
            trimmed = StringUtils.removeStart(trimmed, "```");
            trimmed = StringUtils.removeEnd(trimmed, "```");
            trimmed = StringUtils.trim(trimmed);
        }
        return trimmed;
    }

    private static String jsonHint(Class<?> responseType) {
        return """

                Reply with one JSON object only. Field names must match exactly:
                CategorySuggestion: {"categoryPath": string}
                ListingAnalysis: {"perExampleAnalysis": [string], "winningTemplate": string}
                GeneratedListing: {"title": string, "description": string, "priceAdvice": string, "photoChecklist": [string]}
                Target type: %s.
                """.formatted(responseType.getSimpleName());
    }
}
