package com.example.aiagentpublisher.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptFactoryTest {

    private final PromptFactory prompts = new PromptFactory();

    @Test
    void classifyPromptContainsIdeaAndDemandsRussianCategoryPath() {
        assertThat(prompts.classifySystem()).containsIgnoringCase("olx.kz");
        assertThat(prompts.classifyUser("продаю ноутбуки")).contains("продаю ноутбуки");
    }

    @Test
    void analyzePromptNumbersExamplesAndCarriesCategory() {
        String user = prompts.analyzeUser("Электроника → Ноутбуки", List.of("текст один", "текст два"));
        assertThat(user).contains("Электроника → Ноутбуки");
        assertThat(user).contains("1.").contains("2.");
        assertThat(user).contains("текст один").contains("текст два");
    }

    @Test
    void generatePromptCarriesIdeaTemplateAndExamples() {
        ListingAnalysis analysis = new ListingAnalysis(List.of("а1"), "шаблон успеха");
        String user = prompts.generateUser("продаю ноутбуки", "Электроника → Ноутбуки",
                analysis, List.of("пример"), false);
        assertThat(user).contains("продаю ноутбуки").contains("шаблон успеха").contains("пример");
        assertThat(user).doesNotContain("TOO SIMILAR");
    }

    @Test
    void divergeFlagAddsExplicitDivergeInstruction() {
        ListingAnalysis analysis = new ListingAnalysis(List.of("а1"), "шаблон");
        String user = prompts.generateUser("идея", "категория", analysis, List.of("пример"), true);
        assertThat(user).contains("TOO SIMILAR");
    }

    @Test
    void allSystemPromptsDemandRussianOutput() {
        assertThat(prompts.classifySystem()).containsIgnoringCase("russian");
        assertThat(prompts.analyzeSystem()).containsIgnoringCase("russian");
        assertThat(prompts.generateSystem()).containsIgnoringCase("russian");
    }
}
