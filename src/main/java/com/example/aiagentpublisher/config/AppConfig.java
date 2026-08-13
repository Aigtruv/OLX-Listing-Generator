package com.example.aiagentpublisher.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TelegramClient telegramClient(@Value("${app.telegram.token:}") String token) {
        return new OkHttpTelegramClient(StringUtils.defaultIfBlank(token, "disabled"));
    }

    @Bean
    public RestClient whatsAppRestClient(@Value("${app.whatsapp.graph-base-url}") String graphBaseUrl) {
        return RestClient.builder().baseUrl(graphBaseUrl).build();
    }
}
