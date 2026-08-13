package com.example.aiagentpublisher.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationSessionStore {

    private final Map<Long, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public ConversationSessionStore(Clock clock, @Value("${app.session.ttl:PT24H}") Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public ConversationSession get(long chatId) {
        Instant now = clock.instant();
        ConversationSession session = sessions.get(chatId);
        if (session == null || Duration.between(session.getLastActivity(), now).compareTo(ttl) > 0) {
            session = new ConversationSession(chatId, now);
            sessions.put(chatId, session);
        }
        session.touch(now);
        return session;
    }

    public void reset(long chatId) {
        sessions.remove(chatId);
    }
}
