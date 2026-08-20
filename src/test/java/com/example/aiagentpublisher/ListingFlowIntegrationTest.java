package com.example.aiagentpublisher;

import com.example.aiagentpublisher.bot.ConversationHandler;
import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.CategorySuggestion;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;
import com.example.aiagentpublisher.llm.LlmGateway;
import com.example.aiagentpublisher.olx.OlxListing;
import com.example.aiagentpublisher.olx.OlxListingFetcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1"
})
class ListingFlowIntegrationTest {

    @Autowired
    private ConversationHandler handler;

    @Autowired
    private ListingCaseRepository repository;

    @MockitoBean
    private LlmGateway llmGateway;

    @MockitoBean
    private OlxListingFetcher olxListingFetcher;

    @Test
    void fullFlowPersistsCaseAndPublishesIt() {
        when(llmGateway.generate(anyString(), anyString(), eq(CategorySuggestion.class)))
                .thenReturn(new CategorySuggestion("Электроника → Ноутбуки"));
        when(llmGateway.generate(anyString(), anyString(), eq(ListingAnalysis.class)))
                .thenReturn(new ListingAnalysis(List.of("анализ 1", "анализ 2", "анализ 3"), "шаблон"));
        when(llmGateway.generate(anyString(), anyString(), eq(GeneratedListing.class)))
                .thenReturn(new GeneratedListing("Ноутбук Dell XPS", "Оригинальное описание.",
                        "180 000 тг", List.of("фото экрана")));

        when(olxListingFetcher.isListingUrl(anyString())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            return text != null && text.contains("olx.kz");
        });
        when(olxListingFetcher.fetch(anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return Optional.of(new OlxListing(url, "t", "1 тг", url));
        });

        long chatId = 100L;
        handler.handle(chatId, "/new");
        handler.handle(chatId, "продаю ноутбуки");
        handler.handle(chatId, "да");
        handler.handle(chatId, "https://www.olx.kz/d/obyavlenie/p1.html");
        handler.handle(chatId, "https://www.olx.kz/d/obyavlenie/p2.html");
        handler.handle(chatId, "https://www.olx.kz/d/obyavlenie/p3.html");
        List<String> replies = handler.handle(chatId, "/done");

        assertThat(String.join("\n", replies)).contains("Ноутбук Dell XPS");

        List<ListingCase> cases = repository.findByChatIdOrderByCreatedAtDesc(chatId);
        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getStatus()).isEqualTo(ListingStatus.CREATED);
        assertThat(cases.get(0).getExamples()).hasSize(3);

        handler.handle(chatId, "/published");

        assertThat(repository.findByChatIdOrderByCreatedAtDesc(chatId).get(0).getStatus())
                .isEqualTo(ListingStatus.PUBLISHED);
    }

    @Test
    void failedGenerationKeepsDraftWithExamples() {
        when(llmGateway.generate(anyString(), anyString(), eq(CategorySuggestion.class)))
                .thenReturn(new CategorySuggestion("Электроника → Ноутбуки"));
        when(llmGateway.generate(anyString(), anyString(), eq(ListingAnalysis.class)))
                .thenThrow(new RuntimeException("api down"));
        when(olxListingFetcher.isListingUrl(anyString())).thenReturn(true);
        when(olxListingFetcher.fetch(anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return Optional.of(new OlxListing(url, "t", "1 тг", url));
        });

        long chatId = 101L;
        handler.handle(chatId, "/new");
        handler.handle(chatId, "продаю ноутбуки");
        handler.handle(chatId, "да");
        handler.handle(chatId, "https://www.olx.kz/d/obyavlenie/p1.html");
        handler.handle(chatId, "/done");

        List<ListingCase> cases = repository.findByChatIdOrderByCreatedAtDesc(chatId);
        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(cases.get(0).getIdeaText()).isEqualTo("продаю ноутбуки");
        assertThat(cases.get(0).getExamples()).hasSize(1);
    }
}
