package com.example.aiagentpublisher.leads;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class TelegramSellerNotifier implements SellerNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramSellerNotifier.class);
    private static final int MAX_ATTEMPTS = 3;

    private final String token;
    private final TelegramClient telegramClient;

    public TelegramSellerNotifier(@Value("${app.telegram.token:}") String token,
                                  TelegramClient telegramClient) {
        this.token = token;
        this.telegramClient = telegramClient;
    }

    @Override
    public void notifyNewLead(long chatId, String message) {
        if (StringUtils.isBlank(token)) {
            log.warn("TELEGRAM_BOT_TOKEN is not set — seller ping skipped");
            return;
        }
        TelegramApiException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(message)
                        .build());
                return;
            } catch (TelegramApiException e) {
                last = e;
                log.warn("Seller ping attempt {}/{} failed for chat {}", attempt, MAX_ATTEMPTS, chatId);
            }
        }
        log.error("Failed to ping seller in chat {}", chatId, last);
    }
}
