package com.example.aiagentpublisher.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

public class ListingBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ListingBot.class);

    private final ConversationHandler handler;
    private final TelegramClient telegramClient;

    public ListingBot(ConversationHandler handler, TelegramClient telegramClient) {
        this.handler = handler;
        this.telegramClient = telegramClient;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        long chatId = update.getMessage().getChatId();
        List<String> replies = handler.handle(chatId, update.getMessage().getText());
        for (String reply : replies) {
            try {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(reply)
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to send reply to chat {}", chatId, e);
            }
        }
    }
}
