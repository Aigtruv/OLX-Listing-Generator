package com.example.aiagentpublisher.leads;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final String verifyToken;
    private final String appSecret;
    private final QualificationHandler handler;
    private final WhatsAppSender sender;
    private final WhatsAppWebhookParser parser;
    private final InboundMessageDeduper deduper;

    public WhatsAppWebhookController(@Value("${app.whatsapp.verify-token:}") String verifyToken,
                                     @Value("${app.whatsapp.app-secret:}") String appSecret,
                                     QualificationHandler handler,
                                     WhatsAppSender sender,
                                     WhatsAppWebhookParser parser,
                                     InboundMessageDeduper deduper) {
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
        this.handler = handler;
        this.sender = sender;
        this.parser = parser;
        this.deduper = deduper;
    }

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
                                         @RequestParam("hub.verify_token") String token,
                                         @RequestParam("hub.challenge") String challenge) {
        if (StringUtils.equals(mode, "subscribe") && StringUtils.equals(token, verifyToken)
                && StringUtils.isNotBlank(verifyToken)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody(required = false) String body,
                                        @RequestHeader(value = "X-Hub-Signature-256", required = false)
                                        String signature) {
        if (StringUtils.isNotBlank(appSecret) && !validSignature(body, signature)) {
            return ResponseEntity.status(403).build();
        }
        List<WhatsAppInbound> messages = parser.parse(body);
        for (WhatsAppInbound inbound : messages) {
            if (deduper.isDuplicate(inbound.messageId())) {
                continue;
            }
            List<String> replies;
            try {
                replies = handler.handle(inbound.waId(), inbound.text());
            } catch (Exception e) {
                log.error("Failed to handle WhatsApp message {}", inbound.messageId(), e);
                return ResponseEntity.internalServerError().build();
            }
            deduper.markSeen(inbound.messageId());
            for (String reply : replies) {
                try {
                    sender.sendText(inbound.waId(), reply);
                } catch (Exception e) {
                    log.error("Failed to send reply for WhatsApp message {}", inbound.messageId(), e);
                }
            }
        }
        return ResponseEntity.ok().build();
    }

    private boolean validSignature(String body, String signature) {
        if (StringUtils.isBlank(signature) || !StringUtils.startsWith(signature, "sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(
                    mac.doFinal(StringUtils.defaultString(body).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("WhatsApp signature check failed", e);
            return false;
        }
    }
}
