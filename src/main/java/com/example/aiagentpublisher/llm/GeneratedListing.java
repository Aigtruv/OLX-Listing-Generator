package com.example.aiagentpublisher.llm;

import java.util.List;

public record GeneratedListing(String title, String description, String priceAdvice, List<String> photoChecklist) {
}
