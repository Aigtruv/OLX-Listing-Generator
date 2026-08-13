package com.example.aiagentpublisher.leads;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InboundMessageDeduper {

    private final Map<String, Instant> seenAt = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public InboundMessageDeduper(Clock clock, @Value("${app.session.ttl:PT24H}") Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public boolean isDuplicate(String messageId) {
        Instant now = clock.instant();
        Instant previous = seenAt.get(messageId);
        return previous != null && Duration.between(previous, now).compareTo(ttl) <= 0;
    }

    public void markSeen(String messageId) {
        seenAt.put(messageId, clock.instant());
    }
}
