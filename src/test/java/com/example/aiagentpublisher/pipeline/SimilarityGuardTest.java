package com.example.aiagentpublisher.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarityGuardTest {

    private final SimilarityGuard guard = new SimilarityGuard();

    private static final String EXAMPLE =
            "Продаю отличный мощный ноутбук в идеальном состоянии с гарантией зарядкой и коробкой";

    @Test
    void detectsVerbatimEightWordOverlap() {
        String generated = "Внимание! отличный мощный ноутбук в идеальном состоянии с гарантией — пишите.";
        assertThat(guard.isTooSimilar(generated, List.of(EXAMPLE))).isTrue();
    }

    @Test
    void overlapCheckIsCaseInsensitive() {
        String generated = "ОТЛИЧНЫЙ МОЩНЫЙ НОУТБУК В ИДЕАЛЬНОМ СОСТОЯНИИ С ГАРАНТИЕЙ продам срочно";
        assertThat(guard.isTooSimilar(generated, List.of(EXAMPLE))).isTrue();
    }

    @Test
    void acceptsTextWithoutLongOverlaps() {
        String generated = "Ноутбук для работы и учёбы, быстрый и лёгкий, отдаю с зарядным устройством.";
        assertThat(guard.isTooSimilar(generated, List.of(EXAMPLE))).isFalse();
    }

    @Test
    void shortTextsNeverMatch() {
        assertThat(guard.isTooSimilar("продам ноутбук", List.of("куплю ноутбук"))).isFalse();
        assertThat(guard.isTooSimilar("", List.of(EXAMPLE))).isFalse();
        assertThat(guard.isTooSimilar(null, List.of(EXAMPLE))).isFalse();
    }
}
