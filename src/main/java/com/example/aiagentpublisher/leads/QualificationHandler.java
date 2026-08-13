package com.example.aiagentpublisher.leads;

import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QualificationHandler {

    private static final int MAX_MENU = 10;
    private static final SellerNotifier NO_OP_NOTIFIER = (chatId, message) -> {
    };

    private final QualificationSessionStore sessions;
    private final ListingCaseRepository listings;
    private final LeadRepository leads;
    private final SellerNotifier notifier;

    @Autowired
    public QualificationHandler(QualificationSessionStore sessions, ListingCaseRepository listings,
                                LeadRepository leads, ObjectProvider<SellerNotifier> notifierProvider) {
        this(sessions, listings, leads, notifierProvider.getIfAvailable(() -> NO_OP_NOTIFIER));
    }

    public QualificationHandler(QualificationSessionStore sessions, ListingCaseRepository listings,
                                LeadRepository leads, SellerNotifier notifier) {
        this.sessions = sessions;
        this.listings = listings;
        this.leads = leads;
        this.notifier = notifier;
    }

    public List<String> handle(String buyerWaId, String rawText) {
        String text = StringUtils.trim(rawText);
        QualificationSession session = sessions.get(buyerWaId);
        if (session.getState() != QualificationState.IDLE
                && StringUtils.equalsIgnoreCase(text, "стоп")) {
            sessions.reset(buyerWaId);
            return List.of(QualReplies.STOPPED);
        }
        if (session.getState() == QualificationState.IDLE) {
            return showMenu(session);
        }
        if (StringUtils.isBlank(text)) {
            return List.of(reprompt(session));
        }
        return switch (session.getState()) {
            case PICKING_LISTING -> pickListing(session, text);
            case ASKING_CITY -> {
                session.setCity(text);
                session.setState(QualificationState.ASKING_BUDGET);
                yield List.of(QualReplies.ASK_BUDGET);
            }
            case ASKING_BUDGET -> {
                session.setBudget(text);
                session.setState(QualificationState.ASKING_TIMEFRAME);
                yield List.of(QualReplies.ASK_TIMEFRAME);
            }
            case ASKING_TIMEFRAME -> complete(session, text);
            case IDLE -> showMenu(session);
        };
    }

    private List<String> showMenu(QualificationSession session) {
        List<ListingCase> published = listings.findByStatusOrderByCreatedAtDesc(ListingStatus.PUBLISHED);
        if (published.size() > MAX_MENU) {
            published = published.subList(0, MAX_MENU);
        }
        if (published.isEmpty()) {
            sessions.reset(session.getBuyerWaId());
            return List.of(QualReplies.NOTHING_FOR_SALE);
        }
        session.getMenuListingIds().clear();
        List<String> titles = new ArrayList<>();
        for (ListingCase listingCase : published) {
            session.getMenuListingIds().add(listingCase.getId());
            titles.add(StringUtils.defaultIfBlank(listingCase.getGeneratedTitle(), listingCase.getIdeaText()));
        }
        session.setState(QualificationState.PICKING_LISTING);
        return List.of(QualReplies.menu(titles));
    }

    private List<String> pickListing(QualificationSession session, String text) {
        if (!StringUtils.isNumeric(text)) {
            return List.of(QualReplies.INVALID_CHOICE);
        }
        int index = Integer.parseInt(text) - 1;
        if (index < 0 || index >= session.getMenuListingIds().size()) {
            return List.of(QualReplies.INVALID_CHOICE);
        }
        session.setListingCaseId(session.getMenuListingIds().get(index));
        session.setState(QualificationState.ASKING_CITY);
        return List.of(QualReplies.ASK_CITY);
    }

    private List<String> complete(QualificationSession session, String timeframe) {
        ListingCase listingCase = listings.findById(session.getListingCaseId()).orElse(null);
        if (listingCase == null) {
            sessions.reset(session.getBuyerWaId());
            return List.of(QualReplies.LISTING_GONE);
        }
        Lead lead = new Lead();
        lead.setListingCase(listingCase);
        lead.setBuyerWaId(session.getBuyerWaId());
        lead.setCity(session.getCity());
        lead.setBudget(session.getBudget());
        lead.setTimeframe(timeframe);
        lead.setStatus(LeadStatus.NEW);
        leads.save(lead);
        String title = StringUtils.defaultIfBlank(listingCase.getGeneratedTitle(), listingCase.getIdeaText());
        notifier.notifyNewLead(listingCase.getChatId(),
                QualReplies.sellerPing(title, session.getBuyerWaId(), session.getCity(),
                        session.getBudget(), timeframe));
        sessions.reset(session.getBuyerWaId());
        return List.of(QualReplies.THANKS);
    }

    private String reprompt(QualificationSession session) {
        return switch (session.getState()) {
            case PICKING_LISTING -> QualReplies.INVALID_CHOICE;
            case ASKING_CITY -> QualReplies.ASK_CITY;
            case ASKING_BUDGET -> QualReplies.ASK_BUDGET;
            case ASKING_TIMEFRAME -> QualReplies.ASK_TIMEFRAME;
            case IDLE -> QualReplies.NOTHING_FOR_SALE;
        };
    }
}
