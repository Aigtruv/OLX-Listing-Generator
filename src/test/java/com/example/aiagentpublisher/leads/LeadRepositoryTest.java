package com.example.aiagentpublisher.leads;

import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LeadRepositoryTest {

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private ListingCaseRepository listingCaseRepository;

    @Test
    void savesLeadLinkedToListingCase() {
        ListingCase listingCase = new ListingCase();
        listingCase.setChatId(42L);
        listingCase.setIdeaText("продаю ноутбуки");
        listingCase.setGeneratedTitle("Ноутбук Dell");
        listingCase.setStatus(ListingStatus.PUBLISHED);
        listingCase = listingCaseRepository.saveAndFlush(listingCase);

        Lead lead = new Lead();
        lead.setListingCase(listingCase);
        lead.setBuyerWaId("77011234567");
        lead.setCity("Алматы");
        lead.setBudget("150000");
        lead.setTimeframe("на этой неделе");
        lead.setStatus(LeadStatus.NEW);

        Lead saved = leadRepository.saveAndFlush(lead);
        Lead loaded = leadRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getBuyerWaId()).isEqualTo("77011234567");
        assertThat(loaded.getCity()).isEqualTo("Алматы");
        assertThat(loaded.getBudget()).isEqualTo("150000");
        assertThat(loaded.getTimeframe()).isEqualTo("на этой неделе");
        assertThat(loaded.getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(loaded.getListingCase().getId()).isEqualTo(listingCase.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
    }
}
