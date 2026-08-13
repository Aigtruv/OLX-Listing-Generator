package com.example.aiagentpublisher.leads;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QualificationSessionStore {

    private final Map<String, QualificationSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public QualificationSessionStore(Clock clock, @Value("${app.session.ttl:PT24H}") Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public QualificationSession get(String buyerWaId) {
        Instant now = clock.instant();
        QualificationSession session = sessions.get(buyerWaId);
        if (session == null || Duration.between(session.getLastActivity(), now).compareTo(ttl) > 0) {
            session = new QualificationSession(buyerWaId, now);
            sessions.put(buyerWaId, session);
        }
        session.touch(now);
        return session;
    }

    public void reset(String buyerWaId) {
        sessions.remove(buyerWaId);
    }
}
