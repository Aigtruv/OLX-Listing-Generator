package com.example.aiagentpublisher.pipeline;

import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;

public record PipelineResult(ListingAnalysis analysis, GeneratedListing listing, boolean similarityWarning) {
}
