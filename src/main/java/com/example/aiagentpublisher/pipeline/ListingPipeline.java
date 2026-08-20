package com.example.aiagentpublisher.pipeline;

import com.example.aiagentpublisher.domain.ExampleListing;
import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.CategorySuggestion;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;
import com.example.aiagentpublisher.llm.LlmGateway;
import com.example.aiagentpublisher.llm.PromptFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListingPipeline {

    private final LlmGateway llm;
    private final PromptFactory prompts;
    private final SimilarityGuard similarityGuard;
    private final ListingCaseRepository repository;

    public ListingPipeline(LlmGateway llm, PromptFactory prompts,
                           SimilarityGuard similarityGuard, ListingCaseRepository repository) {
        this.llm = llm;
        this.prompts = prompts;
        this.similarityGuard = similarityGuard;
        this.repository = repository;
    }

    public String classifyCategory(String ideaText) {
        return llm.generate(prompts.classifySystem(), prompts.classifyUser(ideaText),
                CategorySuggestion.class).categoryPath();
    }

    @Transactional
    public PipelineResult run(long chatId, String ideaText, String category, List<String> exampleTexts) {
        ListingAnalysis analysis = llm.generate(prompts.analyzeSystem(),
                prompts.analyzeUser(category, exampleTexts), ListingAnalysis.class);

        GeneratedListing listing = llm.generate(prompts.generateSystem(),
                prompts.generateUser(ideaText, category, analysis, exampleTexts, false),
                GeneratedListing.class);

        boolean similarityWarning = false;
        if (isTooSimilar(listing, exampleTexts)) {
            listing = llm.generate(prompts.generateSystem(),
                    prompts.generateUser(ideaText, category, analysis, exampleTexts, true),
                    GeneratedListing.class);
            similarityWarning = isTooSimilar(listing, exampleTexts);
        }

        repository.save(toCase(chatId, ideaText, category, exampleTexts, analysis, listing,
        repository.findFirstByChatIdAndStatusOrderByCreatedAtDesc(chatId, ListingStatus.DRAFT)
                        .orElseGet(ListingCase::new)));
        return new PipelineResult(analysis, listing, similarityWarning);
    }

    private boolean isTooSimilar(GeneratedListing listing, List<String> exampleTexts) {
        return similarityGuard.isTooSimilar(listing.title() + " " + listing.description(), exampleTexts);
    }

    private ListingCase toCase(long chatId, String ideaText, String category, List<String> exampleTexts,
                               ListingAnalysis analysis, GeneratedListing listing, ListingCase listingCase) {
        listingCase.setChatId(chatId);
        listingCase.setIdeaText(ideaText);
        listingCase.setCategory(category);
        listingCase.setAnalysisSummary(analysis.winningTemplate());
        listingCase.setGeneratedTitle(listing.title());
        listingCase.setGeneratedDescription(listing.description());
        listingCase.setPriceAdvice(listing.priceAdvice());
        listingCase.setStatus(ListingStatus.CREATED);
        listingCase.getExamples().clear();
        List<String> perExample = analysis.perExampleAnalysis() == null
                ? List.of()
                : analysis.perExampleAnalysis();
        for (int i = 0; i < exampleTexts.size(); i++) {
            ExampleListing example = new ExampleListing();
            example.setRawText(exampleTexts.get(i));
            if (i < perExample.size()) {
                example.setAnalysis(perExample.get(i));
            }
            listingCase.getExamples().add(example);
        }
        return listingCase;
    }
}
