package com.example.aiagentpublisher.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.util.UUID;

@Entity
public class ExampleListing {

    @Id
    @GeneratedValue
    private UUID id;

    @Lob
    private String rawText;

    @Lob
    private String analysis;

    public UUID getId() {
        return id;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }
}
