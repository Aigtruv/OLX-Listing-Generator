package com.example.aiagentpublisher;

import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.leads.Lead;
import com.example.aiagentpublisher.leads.LeadRepository;
import com.example.aiagentpublisher.leads.LeadStatus;
import com.example.aiagentpublisher.leads.QualificationHandler;
import com.example.aiagentpublisher.leads.QualReplies;
import com.example.aiagentpublisher.leads.SellerNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:qual-integration;DB_CLOSE_DELAY=-1"
})
class QualificationFlowIntegrationTest {

    @Autowired
    private QualificationHandler handler;

    @Autowired
    private ListingCaseRepository listingCaseRepository;

    @Autowired
    private LeadRepository leadRepository;

    @MockitoBean
    private SellerNotifier sellerNotifier;

    @Test
    void fullBuyerFlowPersistsLeadAndPingsSeller() {
        ListingCase listingCase = new ListingCase();
        listingCase.setChatId(777L);
        listingCase.setIdeaText("продаю ноутбуки");
        listingCase.setGeneratedTitle("Ноутбук Dell XPS");
        listingCase.setStatus(ListingStatus.PUBLISHED);
        listingCaseRepository.saveAndFlush(listingCase);

        handler.handle("77019990000", "привет");
        handler.handle("77019990000", "1");
        handler.handle("77019990000", "Алматы");
        handler.handle("77019990000", "200000");
        List<String> done = handler.handle("77019990000", "завтра");

        assertThat(done).containsExactly(QualReplies.THANKS);

        List<Lead> leads = leadRepository.findAll();
        assertThat(leads).hasSize(1);
        assertThat(leads.get(0).getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(leads.get(0).getBuyerWaId()).isEqualTo("77019990000");
        assertThat(leads.get(0).getCity()).isEqualTo("Алматы");
        assertThat(leads.get(0).getListingCase().getId()).isEqualTo(listingCase.getId());

        verify(sellerNotifier).notifyNewLead(eq(777L), anyString());
    }
}
