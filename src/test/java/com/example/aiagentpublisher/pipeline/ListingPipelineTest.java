package com.example.aiagentpublisher.pipeline;

import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.CategorySuggestion;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;
import com.example.aiagentpublisher.llm.LlmGateway;
import com.example.aiagentpublisher.llm.PromptFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingPipelineTest {

    private static final String EXAMPLE =
            "Продаю отличный мощный ноутбук в идеальном состоянии с гарантией зарядкой и коробкой";

    @Mock
    private LlmGateway llm;

    @Mock
    private ListingCaseRepository repository;

    private ListingPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new ListingPipeline(llm, new PromptFactory(), new SimilarityGuard(), repository);
        lenient().when(repository.save(any(ListingCase.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void classifyDelegatesToLlm() {
        when(llm.generate(anyString(), contains("продаю ноутбуки"), eq(CategorySuggestion.class)))
                .thenReturn(new CategorySuggestion("Электроника → Ноутбуки"));

        assertThat(pipeline.classifyCategory("продаю ноутбуки")).isEqualTo("Электроника → Ноутбуки");
    }

    @Test
    void happyPathSavesCreatedCaseWithPerExampleAnalysis() {
        when(llm.generate(anyString(), anyString(), eq(ListingAnalysis.class)))
                .thenReturn(new ListingAnalysis(List.of("анализ 1"), "шаблон"));
        when(llm.generate(anyString(), anyString(), eq(GeneratedListing.class)))
                .thenReturn(new GeneratedListing("Ноутбук для учёбы", "Совсем другое описание без совпадений.",
                        "150 000 тг", List.of("фото экрана")));

        PipelineResult result = pipeline.run(9L, "продаю ноутбуки", "Электроника → Ноутбуки", List.of(EXAMPLE));

        assertThat(result.similarityWarning()).isFalse();
        assertThat(result.listing().title()).isEqualTo("Ноутбук для учёбы");

        ArgumentCaptor<ListingCase> captor = ArgumentCaptor.forClass(ListingCase.class);
        verify(repository).save(captor.capture());
        ListingCase saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ListingStatus.CREATED);
        assertThat(saved.getChatId()).isEqualTo(9L);
        assertThat(saved.getGeneratedTitle()).isEqualTo("Ноутбук для учёбы");
        assertThat(saved.getAnalysisSummary()).isEqualTo("шаблон");
        assertThat(saved.getExamples()).hasSize(1);
        assertThat(saved.getExamples().get(0).getRawText()).isEqualTo(EXAMPLE);
        assertThat(saved.getExamples().get(0).getAnalysis()).isEqualTo("анализ 1");
    }

    @Test
    void regeneratesOnceWhenTooSimilarThenSucceeds() {
        when(llm.generate(anyString(), anyString(), eq(ListingAnalysis.class)))
                .thenReturn(new ListingAnalysis(List.of("а"), "шаблон"));
        when(llm.generate(anyString(), anyString(), eq(GeneratedListing.class)))
                .thenReturn(
                        new GeneratedListing("Копия", EXAMPLE, "100", List.of("ф")),
                        new GeneratedListing("Оригинал", "Полностью новое описание своими словами.", "100", List.of("ф")));

        PipelineResult result = pipeline.run(1L, "идея", "категория", List.of(EXAMPLE));

        assertThat(result.similarityWarning()).isFalse();
        assertThat(result.listing().title()).isEqualTo("Оригинал");
        verify(llm, times(2)).generate(anyString(), anyString(), eq(GeneratedListing.class));
    }

    @Test
    void keepsWarningWhenRegenerationStillTooSimilar() {
        when(llm.generate(anyString(), anyString(), eq(ListingAnalysis.class)))
                .thenReturn(new ListingAnalysis(List.of("а"), "шаблон"));
        when(llm.generate(anyString(), anyString(), eq(GeneratedListing.class)))
                .thenReturn(
                        new GeneratedListing("Копия", EXAMPLE, "100", List.of("ф")),
                        new GeneratedListing("Копия 2", EXAMPLE, "100", List.of("ф")));

        PipelineResult result = pipeline.run(1L, "идея", "категория", List.of(EXAMPLE));

        assertThat(result.similarityWarning()).isTrue();
        assertThat(result.listing().title()).isEqualTo("Копия 2");
    }
}
