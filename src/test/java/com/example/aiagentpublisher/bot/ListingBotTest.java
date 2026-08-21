package com.example.aiagentpublisher.bot;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingBotTest {

    @Mock
    private ConversationHandler handler;

    @Mock
    private TelegramClient telegramClient;

    private Update textUpdate(long chatId, String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getText()).thenReturn(text);
        return update;
    }

    private ConversationSessionStore idleStore() {
        return new ConversationSessionStore(Clock.systemUTC(), Duration.ofHours(24));
    }

    @Test
    void forwardsTextAndSendsEachReply() throws TelegramApiException {
        when(handler.handle(42L, "/new")).thenReturn(List.of("ответ 1", "ответ 2"));
        ListingBot bot = new ListingBot(handler, telegramClient, idleStore());

        bot.consume(textUpdate(42L, "/new"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(2)).execute(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SendMessage::getChatId, SendMessage::getText)
                .containsExactly(
                        Tuple.tuple("42", "ответ 1"),
                        Tuple.tuple("42", "ответ 2"));
    }

    @Test
    void ignoresUpdatesWithoutTextMessage() {
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(false);
        ListingBot bot = new ListingBot(handler, telegramClient, idleStore());

        bot.consume(update);

        verifyNoInteractions(handler, telegramClient);
    }

    @Test
    void doneSendsGeneratingAckBeforeHandlerReplies() throws TelegramApiException {
        when(handler.handle(42L, "/done")).thenReturn(List.of("результат"));
        ListingBot bot = new ListingBot(handler, telegramClient, idleStore());

        bot.consume(textUpdate(42L, "/done"));

        InOrder order = inOrder(telegramClient, handler);
        ArgumentCaptor<SendMessage> first = ArgumentCaptor.forClass(SendMessage.class);
        order.verify(telegramClient).execute(first.capture());
        assertThat(first.getValue().getText()).isEqualTo(BotReplies.GENERATING);
        order.verify(handler).handle(42L, "/done");
        ArgumentCaptor<SendMessage> second = ArgumentCaptor.forClass(SendMessage.class);
        order.verify(telegramClient).execute(second.capture());
        assertThat(second.getValue().getText()).isEqualTo("результат");
    }

    @Test
    void handlerCrashSendsLlmError() throws TelegramApiException {
        when(handler.handle(42L, "x")).thenThrow(new RuntimeException("boom"));
        ListingBot bot = new ListingBot(handler, telegramClient, idleStore());

        bot.consume(textUpdate(42L, "x"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).isEqualTo(BotReplies.LLM_ERROR);
    }

    @Test
    void ideaWhileAwaitingIdeaSendsSearchingAckFirst() throws TelegramApiException {
        ConversationSessionStore store =
                new ConversationSessionStore(Clock.systemUTC(), Duration.ofHours(24));
        store.get(42L).setState(ConversationState.AWAITING_IDEA);
        when(handler.handle(42L, "gps трекеры")).thenReturn(List.of("1. …"));
        ListingBot bot = new ListingBot(handler, telegramClient, store);

        bot.consume(textUpdate(42L, "gps трекеры"));

        InOrder order = inOrder(telegramClient, handler);
        ArgumentCaptor<SendMessage> first = ArgumentCaptor.forClass(SendMessage.class);
        order.verify(telegramClient).execute(first.capture());
        assertThat(first.getValue().getText()).isEqualTo(BotReplies.SOURCING_SEARCHING);
        order.verify(handler).handle(42L, "gps трекеры");
    }
}
