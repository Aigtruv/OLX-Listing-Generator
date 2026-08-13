package com.example.aiagentpublisher.leads;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QualificationSessionStoreTest {

    @Test
    void returnsSameSessionWithinTtl() {
        QualificationSessionStore store =
                new QualificationSessionStore(Clock.systemUTC(), Duration.ofHours(24));

        store.get("7701").setState(QualificationState.ASKING_CITY);

        assertThat(store.get("7701").getState()).isEqualTo(QualificationState.ASKING_CITY);
    }

    @Test
    void expiresSessionAfterTtl() {
        Instant start = Instant.parse("2026-08-13T10:00:00Z");
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(start, start.plus(Duration.ofHours(25)));
        QualificationSessionStore store = new QualificationSessionStore(clock, Duration.ofHours(24));

        store.get("7701").setState(QualificationState.PICKING_LISTING);

        assertThat(store.get("7701").getState()).isEqualTo(QualificationState.IDLE);
    }

    @Test
    void resetDropsSession() {
        QualificationSessionStore store =
                new QualificationSessionStore(Clock.fixed(Instant.now(), ZoneOffset.UTC), Duration.ofHours(24));
        store.get("7701").setState(QualificationState.ASKING_BUDGET);

        store.reset("7701");

        assertThat(store.get("7701").getState()).isEqualTo(QualificationState.IDLE);
    }
}
