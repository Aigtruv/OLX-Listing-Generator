package com.example.aiagentpublisher.leads;

import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualificationHandlerTest {

    @Mock
    private ListingCaseRepository listings;

    @Mock
    private LeadRepository leads;

    @Mock
    private SellerNotifier notifier;

    private QualificationHandler handler;

    @BeforeEach
    void setUp() {
        QualificationSessionStore store =
                new QualificationSessionStore(Clock.systemUTC(), Duration.ofHours(24));
        handler = new QualificationHandler(store, listings, leads, notifier);
    }

    private ListingCase published(String title, long chatId) {
        ListingCase listingCase = new ListingCase();
        listingCase.setChatId(chatId);
        listingCase.setGeneratedTitle(title);
        listingCase.setIdeaText("идея");
        listingCase.setStatus(ListingStatus.PUBLISHED);
        try {
            var field = ListingCase.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(listingCase, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return listingCase;
    }

    @Test
    void noPublishedListingsStaysIdle() {
        when(listings.findByStatusOrderByCreatedAtDesc(ListingStatus.PUBLISHED)).thenReturn(List.of());

        assertThat(handler.handle("7701", "привет")).containsExactly(QualReplies.NOTHING_FOR_SALE);
        verifyNoInteractions(leads, notifier);
    }

    @Test
    void happyPathSavesLeadAndNotifiesSeller() {
        ListingCase listing = published("Ноутбук Dell", 99L);
        when(listings.findByStatusOrderByCreatedAtDesc(ListingStatus.PUBLISHED)).thenReturn(List.of(listing));
        when(listings.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(leads.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        List<String> menu = handler.handle("7701", "привет");
        assertThat(menu.get(0)).contains("1. Ноутбук Dell");

        assertThat(handler.handle("7701", "1")).containsExactly(QualReplies.ASK_CITY);
        assertThat(handler.handle("7701", "Алматы")).containsExactly(QualReplies.ASK_BUDGET);
        assertThat(handler.handle("7701", "150000")).containsExactly(QualReplies.ASK_TIMEFRAME);
        List<String> done = handler.handle("7701", "на этой неделе");

        assertThat(done).containsExactly(QualReplies.THANKS);
        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leads).save(captor.capture());
        Lead saved = captor.getValue();
        assertThat(saved.getBuyerWaId()).isEqualTo("7701");
        assertThat(saved.getCity()).isEqualTo("Алматы");
        assertThat(saved.getBudget()).isEqualTo("150000");
        assertThat(saved.getTimeframe()).isEqualTo("на этой неделе");
        assertThat(saved.getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(saved.getListingCase()).isSameAs(listing);
        verify(notifier).notifyNewLead(99L, QualReplies.sellerPing("Ноутбук Dell", "7701",
                "Алматы", "150000", "на этой неделе"));
    }

    @Test
    void invalidMenuChoiceStaysOnPick() {
        ListingCase listing = published("Ноутбук Dell", 1L);
        when(listings.findByStatusOrderByCreatedAtDesc(ListingStatus.PUBLISHED)).thenReturn(List.of(listing));

        handler.handle("7701", "привет");
        assertThat(handler.handle("7701", "9")).containsExactly(QualReplies.INVALID_CHOICE);
        verify(leads, never()).save(any());
    }

    @Test
    void stopClearsSessionWithoutSaving() {
        ListingCase listing = published("Ноутбук Dell", 1L);
        when(listings.findByStatusOrderByCreatedAtDesc(ListingStatus.PUBLISHED)).thenReturn(List.of(listing));

        handler.handle("7701", "привет");
        handler.handle("7701", "1");
        assertThat(handler.handle("7701", "СТОП")).containsExactly(QualReplies.STOPPED);
        verify(leads, never()).save(any());
        verifyNoInteractions(notifier);

        List<String> again = handler.handle("7701", "привет");
        assertThat(again.get(0)).contains("1. Ноутбук Dell");
    }

    @Test
    void blankAnswerRePromptsSameQuestion() {
        ListingCase listing = published("Ноутбук Dell", 1L);
        when(listings.findByStatusOrderByCreatedAtDesc(ListingStatus.PUBLISHED)).thenReturn(List.of(listing));

        handler.handle("7701", "привет");
        handler.handle("7701", "1");
        assertThat(handler.handle("7701", "   ")).containsExactly(QualReplies.ASK_CITY);
    }
}
