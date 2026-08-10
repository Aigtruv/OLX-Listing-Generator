package com.example.aiagentpublisher.bot;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void forwardsTextAndSendsEachReply() throws TelegramApiException {
        when(handler.handle(42L, "/new")).thenReturn(List.of("ответ 1", "ответ 2"));
        ListingBot bot = new ListingBot(handler, telegramClient);

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
        ListingBot bot = new ListingBot(handler, telegramClient);

        bot.consume(update);

        verifyNoInteractions(handler, telegramClient);
    }
}
