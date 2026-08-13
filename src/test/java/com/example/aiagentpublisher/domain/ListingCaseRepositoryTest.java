package com.example.aiagentpublisher.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ListingCaseRepositoryTest {

    @Autowired
    private ListingCaseRepository repository;

    private ListingCase newCase(long chatId, ListingStatus status, Instant createdAt, String title) {
        ListingCase listingCase = new ListingCase();
        listingCase.setChatId(chatId);
        listingCase.setIdeaText("продаю ноутбуки");
        listingCase.setCategory("Электроника → Ноутбуки");
        listingCase.setStatus(status);
        listingCase.setCreatedAt(createdAt);
        listingCase.setGeneratedTitle(title);
        return listingCase;
    }

    @Test
    void savesCaseWithExamplesAndReadsThemBackInOrder() {
        ListingCase listingCase = newCase(1L, ListingStatus.CREATED, null, "t");
        ExampleListing first = new ExampleListing();
        first.setRawText("пример 1");
        ExampleListing second = new ExampleListing();
        second.setRawText("пример 2");
        listingCase.getExamples().add(first);
        listingCase.getExamples().add(second);

        UUID id = repository.saveAndFlush(listingCase).getId();
        ListingCase loaded = repository.findById(id).orElseThrow();

        assertThat(loaded.getExamples()).extracting(ExampleListing::getRawText)
                .containsExactly("пример 1", "пример 2");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(ListingStatus.CREATED);
    }

    @Test
    void findsLatestCreatedCaseForChat() {
        Instant base = Instant.parse("2026-08-10T10:00:00Z");
        repository.save(newCase(7L, ListingStatus.CREATED, base, "older"));
        repository.save(newCase(7L, ListingStatus.CREATED, base.plusSeconds(60), "newer"));
        repository.save(newCase(7L, ListingStatus.PUBLISHED, base.plusSeconds(120), "published"));
        repository.save(newCase(8L, ListingStatus.CREATED, base.plusSeconds(180), "other chat"));
        repository.flush();

        ListingCase latest = repository
                .findFirstByChatIdAndStatusOrderByCreatedAtDesc(7L, ListingStatus.CREATED)
                .orElseThrow();

        assertThat(latest.getGeneratedTitle()).isEqualTo("newer");
    }

    @Test
    void findsPublishedCasesNewestFirst() {
        Instant base = Instant.parse("2026-08-13T10:00:00Z");
        repository.save(newCase(1L, ListingStatus.CREATED, base, "created"));
        repository.save(newCase(1L, ListingStatus.PUBLISHED, base.plusSeconds(10), "older published"));
        repository.save(newCase(2L, ListingStatus.PUBLISHED, base.plusSeconds(20), "newer published"));
        repository.flush();

        assertThat(repository.findByStatusOrderByCreatedAtDesc(ListingStatus.PUBLISHED))
                .extracting(ListingCase::getGeneratedTitle)
                .containsExactly("newer published", "older published");
    }
}
