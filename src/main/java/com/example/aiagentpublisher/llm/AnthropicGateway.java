package com.example.aiagentpublisher.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnthropicGateway implements LlmGateway {

    private final String model;
    private final Object lock = new Object();
    private volatile AnthropicClient client;

    public AnthropicGateway(@Value("${app.anthropic.model}") String model) {
        this.model = model;
    }

    // Lazy so the app can start without ANTHROPIC_API_KEY (fails on first use instead).
    private AnthropicClient client() {
        if (client == null) {
            synchronized (lock) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder()
                            .fromEnv()
                            .maxRetries(3)
                            .build();
                }
            }
        }
        return client;
    }

    @Override
    public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(systemPrompt)
                .outputConfig(responseType)
                .addUserMessage(userPrompt)
                .build();
        return client().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Claude returned no text content"))
                .text();
    }
}
