package com.example.aiagentpublisher.leads;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WhatsAppWebhookParser {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookParser.class);
    private final ObjectMapper mapper = new ObjectMapper();

    public List<WhatsAppInbound> parse(String json) {
        List<WhatsAppInbound> result = new ArrayList<>();
        if (StringUtils.isBlank(json)) {
            return List.copyOf(result);
        }
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode entries = root.path("entry");
            if (!entries.isArray()) {
                return List.of();
            }
            for (JsonNode entry : entries) {
                JsonNode changes = entry.path("changes");
                if (!changes.isArray()) {
                    continue;
                }
                for (JsonNode change : changes) {
                    JsonNode messages = change.path("value").path("messages");
                    if (!messages.isArray()) {
                        continue;
                    }
                    for (JsonNode message : messages) {
                        if (!StringUtils.equals(message.path("type").asText(), "text")) {
                            continue;
                        }
                        String waId = message.path("from").asText();
                        String id = message.path("id").asText();
                        String text = message.path("text").path("body").asText();
                        if (StringUtils.isAnyBlank(waId, id)) {
                            continue;
                        }
                        result.add(new WhatsAppInbound(waId, id, text));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse WhatsApp webhook payload", e);
            return List.of();
        }
        return List.copyOf(result);
    }
}
