package com.example.aiagentpublisher.leads;

import com.example.aiagentpublisher.domain.ListingCase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;

import java.time.Instant;
import java.util.UUID;

@Entity
public class Lead {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "listing_case_id")
    private ListingCase listingCase;

    @Column(nullable = false)
    private String buyerWaId;

    @Column(length = 1000)
    private String city;

    @Column(length = 1000)
    private String budget;

    @Column(length = 1000)
    private String timeframe;

    @Enumerated(EnumType.STRING)
    private LeadStatus status;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public ListingCase getListingCase() {
        return listingCase;
    }

    public void setListingCase(ListingCase listingCase) {
        this.listingCase = listingCase;
    }

    public String getBuyerWaId() {
        return buyerWaId;
    }

    public void setBuyerWaId(String buyerWaId) {
        this.buyerWaId = buyerWaId;
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

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public void setStatus(LeadStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
