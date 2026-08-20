package com.example.aiagentpublisher.bot;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

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
        String text = update.getMessage().getText();
        if (StringUtils.equals(text, "/done")) {
            send(chatId, BotReplies.GENERATING);
        }
        try {
            for (String reply : handler.handle(chatId, text)) {
                send(chatId, reply);
            }
        } catch (RuntimeException e) {
            log.error("Failed to handle message from chat {}", chatId, e);
            send(chatId, BotReplies.LLM_ERROR);
        }
    }

    private void send(long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to send reply to chat {}", chatId, e);
        }
    }
}
