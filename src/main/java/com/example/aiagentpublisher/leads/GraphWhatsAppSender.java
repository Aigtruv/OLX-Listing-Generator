package com.example.aiagentpublisher.leads;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class GraphWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(GraphWhatsAppSender.class);
    private static final int MAX_ATTEMPTS = 3;

    private final String token;
    private final String phoneNumberId;
    private final RestClient restClient;

    public GraphWhatsAppSender(@Value("${app.whatsapp.token:}") String token,
                               @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
                               RestClient restClient) {
        this.token = token;
        this.phoneNumberId = phoneNumberId;
        this.restClient = restClient;
    }

    @Override
    public void sendText(String toWaId, String text) {
        if (StringUtils.isAnyBlank(token, phoneNumberId)) {
            log.warn("WhatsApp is not configured — outbound send skipped");
            return;
        }
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", toWaId,
                "type", "text",
                "text", Map.of("body", text));
        RestClientException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                restClient.post()
                        .uri("/{phoneNumberId}/messages", phoneNumberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientException e) {
                last = e;
                log.warn("WhatsApp send attempt {}/{} failed for {}", attempt, MAX_ATTEMPTS, toWaId);
            }
        }
        log.error("Failed to send WhatsApp message to {}", toWaId, last);
    }
}
