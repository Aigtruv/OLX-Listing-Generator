package com.example.aiagentpublisher.bot;

import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

@Component
public class TelegramBotStarter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotStarter.class);

    private final ConversationHandler handler;
    private final String token;
    private TelegramBotsLongPollingApplication botsApplication;

    public TelegramBotStarter(ConversationHandler handler,
                              @Value("${app.telegram.token:}") String token) {
        this.handler = handler;
        this.token = token;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (StringUtils.isBlank(token)) {
            log.warn("TELEGRAM_BOT_TOKEN is not set — Telegram bot not started");
            return;
        }
        botsApplication = new TelegramBotsLongPollingApplication();
        botsApplication.registerBot(token, new ListingBot(handler, new OkHttpTelegramClient(token)));
        log.info("Telegram bot started (long polling)");
    }

    @PreDestroy
    public void shutdown() throws Exception {
        if (botsApplication != null) {
            botsApplication.close();
        }
    }
}
