package com.example.aiagentpublisher.leads;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InboundMessageDeduperTest {

    @Test
    void checkingDoesNotRecordUntilMarkedSeen() {
        InboundMessageDeduper deduper =
                new InboundMessageDeduper(Clock.systemUTC(), Duration.ofHours(24));

        assertThat(deduper.isDuplicate("wamid.1")).isFalse();
        assertThat(deduper.isDuplicate("wamid.1")).isFalse();

        deduper.markSeen("wamid.1");

        assertThat(deduper.isDuplicate("wamid.1")).isTrue();
    }

    @Test
    void expiresAfterTtl() {
        Instant start = Instant.parse("2026-08-13T10:00:00Z");
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(start, start.plus(Duration.ofHours(25)));
        InboundMessageDeduper deduper = new InboundMessageDeduper(clock, Duration.ofHours(24));

        deduper.markSeen("wamid.1");

        assertThat(deduper.isDuplicate("wamid.1")).isFalse();
    }
}
