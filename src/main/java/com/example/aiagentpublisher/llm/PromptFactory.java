package com.example.aiagentpublisher.llm;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptFactory {

    public String classifySystem() {
        return """
                You are an expert on the OLX.kz classifieds marketplace and its category tree.
                The user gives you a product idea in Russian. Pick the single most fitting
                OLX.kz category path. Answer with the category path in Russian, using the
                format "Раздел → Подраздел", e.g. "Электроника → Ноутбуки и компьютеры".
                """;
    }

    public String classifyUser(String idea) {
        return "Product idea: «%s». Return the best OLX.kz category path.".formatted(idea);
    }

    public String analyzeSystem() {
        return """
                You are a marketplace listing analyst for OLX.kz. You explain why specific
                listings perform well: title hooks, description structure, price positioning,
                photo mentions, trust signals (warranty, receipts, seller tone).
                All output text must be in Russian.
                """;
    }

    public String analyzeUser(String category, List<String> examples) {
        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(category).append("\n\n");
        sb.append("Example listings from this category, one per item:\n\n");
        for (int i = 0; i < examples.size(); i++) {
            sb.append(i + 1).append(". ").append(examples.get(i)).append("\n\n");
        }
        sb.append("""
                For each example, in the same order, write a short analysis (in Russian) of why
                it works or fails: title hook, description structure, price positioning, photos,
                trust signals. Then write a summary "winning template" (in Russian) describing
                what a top listing in this category looks like.
                perExampleAnalysis must contain exactly one entry per example, same order.
                """);
        return sb.toString();
    }

    public String generateSystem() {
        return """
                You write original OLX.kz listings in Russian. You never copy sentences or
                distinctive phrases from example listings — the result must not look like any
                of them. You only state facts the user provided; for unknown specifics use
                placeholders in square brackets, e.g. [укажите модель]. The title must be at
                most 70 characters. All output text must be in Russian.
                """;
    }

    public String generateUser(String idea, String category, ListingAnalysis analysis,
                               List<String> examples, boolean diverge) {
        StringBuilder sb = new StringBuilder();
        sb.append("Product idea: ").append(idea).append("\n");
        sb.append("Category: ").append(category).append("\n\n");
        sb.append("Winning template for this category:\n").append(analysis.winningTemplate()).append("\n\n");
        sb.append("Example listings you must NOT resemble:\n\n");
        for (int i = 0; i < examples.size(); i++) {
            sb.append(i + 1).append(". ").append(examples.get(i)).append("\n\n");
        }
        sb.append("""
                Write one original listing: title (max 70 chars), description, recommended
                price range with one-line reasoning (priceAdvice), and a photo checklist
                (photoChecklist) of 3-6 concrete shots the seller should take.
                """);
        if (diverge) {
            sb.append("""

                    Your previous attempt was TOO SIMILAR to the examples. Rewrite from scratch
                    with different wording, structure and openings. Do not reuse any 8-word
                    sequence from the examples.
                    """);
        }
        return sb.toString();
    }
}
