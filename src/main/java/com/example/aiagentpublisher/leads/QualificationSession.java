package com.example.aiagentpublisher.leads;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QualificationSession {

    private final String buyerWaId;
    private QualificationState state = QualificationState.IDLE;
    private final List<UUID> menuListingIds = new ArrayList<>();
    private UUID listingCaseId;
    private String city;
    private String budget;
    private Instant lastActivity;

    public QualificationSession(String buyerWaId, Instant now) {
        this.buyerWaId = buyerWaId;
        this.lastActivity = now;
    }

    public void touch(Instant now) {
        this.lastActivity = now;
    }

    public String getBuyerWaId() {
        return buyerWaId;
    }

    public QualificationState getState() {
        return state;
    }

    public void setState(QualificationState state) {
        this.state = state;
    }

    public List<UUID> getMenuListingIds() {
        return menuListingIds;
    }

    public UUID getListingCaseId() {
        return listingCaseId;
    }

    public void setListingCaseId(UUID listingCaseId) {
        this.listingCaseId = listingCaseId;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getBudget() {
        return budget;
    }

    public void setBudget(String budget) {
        this.budget = budget;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }
}
