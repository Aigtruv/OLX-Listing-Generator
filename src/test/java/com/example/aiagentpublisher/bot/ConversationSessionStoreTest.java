package com.example.aiagentpublisher.bot;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationSessionStoreTest {

    @Test
    void returnsSameSessionWithinTtl() {
        ConversationSessionStore store =
                new ConversationSessionStore(Clock.systemUTC(), Duration.ofHours(24));

        ConversationSession first = store.get(1L);
        first.setState(ConversationState.AWAITING_IDEA);

        assertThat(store.get(1L).getState()).isEqualTo(ConversationState.AWAITING_IDEA);
    }

    @Test
    void expiresSessionAfterTtl() {
        Instant start = Instant.parse("2026-08-10T10:00:00Z");
        Clock clock = mock(Clock.class);
        // get() reads the clock exactly once per call: first get -> start, second get -> +25h
        when(clock.instant()).thenReturn(start, start.plus(Duration.ofHours(25)));
        ConversationSessionStore store = new ConversationSessionStore(clock, Duration.ofHours(24));

        store.get(1L).setState(ConversationState.COLLECTING_EXAMPLES);

        assertThat(store.get(1L).getState()).isEqualTo(ConversationState.IDLE);
    }

    @Test
    void resetDropsSession() {
        ConversationSessionStore store =
                new ConversationSessionStore(Clock.fixed(Instant.now(), ZoneOffset.UTC), Duration.ofHours(24));
        store.get(1L).setState(ConversationState.AWAITING_IDEA);

        store.reset(1L);

        assertThat(store.get(1L).getState()).isEqualTo(ConversationState.IDLE);
    }
}
