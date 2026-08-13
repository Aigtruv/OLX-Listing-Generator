package com.example.aiagentpublisher.llm;

public interface LlmGateway {

    <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType);
}
