package com.example.aiagentpublisher.leads;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramSellerNotifierTest {

    @Mock
    private TelegramClient telegramClient;

    @Test
    void sendsMessageWhenTokenPresent() throws TelegramApiException {
        TelegramSellerNotifier notifier = new TelegramSellerNotifier("token", telegramClient);

        notifier.notifyNewLead(42L, "лид");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getChatId()).isEqualTo("42");
        assertThat(captor.getValue().getText()).isEqualTo("лид");
    }

    @Test
    void skipsSendWhenTokenBlank() throws TelegramApiException {
        TelegramSellerNotifier notifier = new TelegramSellerNotifier("  ", telegramClient);

        notifier.notifyNewLead(42L, "лид");

        verify(telegramClient, never()).execute(any(SendMessage.class));
    }

    @Test
    void retriesThenGivesUp() throws TelegramApiException {
        TelegramSellerNotifier notifier = new TelegramSellerNotifier("token", telegramClient);
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("down"));

        notifier.notifyNewLead(1L, "лид");

        verify(telegramClient, times(3)).execute(any(SendMessage.class));
    }
}
