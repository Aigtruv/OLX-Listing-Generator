package com.example.aiagentpublisher.olx;

public record OlxListing(String url, String title, String price, String description) {

    public String formatForPipeline() {
        return url + "\n" + title + "\n" + price + "\n" + description;
    }
}
