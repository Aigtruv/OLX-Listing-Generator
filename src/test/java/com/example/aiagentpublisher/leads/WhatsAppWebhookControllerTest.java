package com.example.aiagentpublisher.leads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    @Mock
    private QualificationHandler handler;

    @Mock
    private WhatsAppSender sender;

    private WhatsAppWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new WhatsAppWebhookController(
                "verify-me",
                "",
                handler,
                sender,
                new WhatsAppWebhookParser(),
                new InboundMessageDeduper(Clock.systemUTC(), Duration.ofHours(24)));
    }

    @Test
    void verifyReturnsChallenge() {
        ResponseEntity<String> response = controller.verify("subscribe", "verify-me", "challenge-9");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("challenge-9");
    }

    @Test
    void verifyRejectsBadToken() {
        ResponseEntity<String> response = controller.verify("subscribe", "wrong", "challenge-9");
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void postForwardsTextAndSendsReplies() {
        when(handler.handle("77011234567", "привет")).thenReturn(List.of("ответ"));
        String json = """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "messages":[{"from":"77011234567","id":"wamid.1","type":"text","text":{"body":"привет"}}]
                }}]}]}
                """;

        ResponseEntity<Void> response = controller.receive(json, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(sender).sendText("77011234567", "ответ");
    }

    @Test
    void duplicateMessageIdIsIgnored() {
        when(handler.handle("77011234567", "привет")).thenReturn(List.of("ответ"));
        String json = """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "messages":[{"from":"77011234567","id":"wamid.dup","type":"text","text":{"body":"привет"}}]
                }}]}]}
                """;
        controller.receive(json, null);
        controller.receive(json, null);
        verify(handler).handle("77011234567", "привет");
        verify(sender).sendText("77011234567", "ответ");
    }

    @Test
    void rejectsPostWhenAppSecretSetAndSignatureMissing() {
        WhatsAppWebhookController secured = new WhatsAppWebhookController(
                "verify-me",
                "app-secret",
                handler,
                sender,
                new WhatsAppWebhookParser(),
                new InboundMessageDeduper(Clock.systemUTC(), Duration.ofHours(24)));
        ResponseEntity<Void> response = secured.receive("{}", null);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(handler, sender);
    }
}
