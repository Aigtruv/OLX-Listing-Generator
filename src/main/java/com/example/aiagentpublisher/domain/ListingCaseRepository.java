package com.example.aiagentpublisher.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingCaseRepository extends JpaRepository<ListingCase, UUID> {

    List<ListingCase> findByChatIdOrderByCreatedAtDesc(long chatId);

    Optional<ListingCase> findFirstByChatIdAndStatusOrderByCreatedAtDesc(long chatId, ListingStatus status);
}
