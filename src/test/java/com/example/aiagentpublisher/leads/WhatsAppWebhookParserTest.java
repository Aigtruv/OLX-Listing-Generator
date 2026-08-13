package com.example.aiagentpublisher.leads;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppWebhookParserTest {

    private final WhatsAppWebhookParser parser = new WhatsAppWebhookParser();

    @Test
    void extractsTextMessage() {
        String json = """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "messages":[{"from":"77011234567","id":"wamid.1","type":"text","text":{"body":"привет"}}]
                }}]}]}
                """;
        List<WhatsAppInbound> messages = parser.parse(json);
        assertThat(messages).containsExactly(new WhatsAppInbound("77011234567", "wamid.1", "привет"));
    }

    @Test
    void ignoresNonText() {
        String json = """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "messages":[{"from":"7701","id":"wamid.2","type":"image"}]
                }}]}]}
                """;
        assertThat(parser.parse(json)).isEmpty();
    }

    @Test
    void emptyOrInvalidJsonYieldsEmptyList() {
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("{")).isEmpty();
        assertThat(parser.parse("{\"entry\":[]}")).isEmpty();
    }
}
