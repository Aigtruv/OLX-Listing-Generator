package com.example.aiagentpublisher.bot;

import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;
import com.example.aiagentpublisher.pipeline.ListingPipeline;
import com.example.aiagentpublisher.pipeline.PipelineResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationHandlerTest {

    @Mock
    private ListingPipeline pipeline;

    @Mock
    private ListingCaseRepository repository;

    private ConversationHandler handler;

    private static PipelineResult okResult() {
        return new PipelineResult(
                new ListingAnalysis(List.of("анализ 1", "анализ 2", "анализ 3"), "шаблон успеха"),
                new GeneratedListing("Ноутбук Dell", "Описание без совпадений.", "150 000 тг",
                        List.of("фото экрана", "фото клавиатуры")),
                false);
    }

    @BeforeEach
    void setUp() {
        ConversationSessionStore store =
                new ConversationSessionStore(Clock.systemUTC(), Duration.ofHours(24));
        handler = new ConversationHandler(store, pipeline, repository);
    }

    private void driveToExamples(long chatId) {
        when(pipeline.classifyCategory("продаю ноутбуки")).thenReturn("Электроника → Ноутбуки");
        handler.handle(chatId, "/new");
        handler.handle(chatId, "продаю ноутбуки");
        handler.handle(chatId, "да");
    }

    @Test
    void fullHappyFlowProducesListingMessages() {
        driveToExamples(1L);
        when(pipeline.run(eq(1L), eq("продаю ноутбуки"), eq("Электроника → Ноутбуки"),
                eq(List.of("пример 1", "пример 2", "пример 3")))).thenReturn(okResult());

        handler.handle(1L, "пример 1");
        handler.handle(1L, "пример 2");
        handler.handle(1L, "пример 3");
        List<String> replies = handler.handle(1L, "/done");

        String all = String.join("\n", replies);
        assertThat(all).contains("Ноутбук Dell").contains("шаблон успеха")
                .contains("150 000 тг").contains("фото экрана");
        assertThat(all).doesNotContain(BotReplies.SIMILARITY_WARNING);
    }

    @Test
    void categoryCorrectionUsesUserText() {
        when(pipeline.classifyCategory("идея")).thenReturn("Неверная категория");
        when(pipeline.run(anyLong(), anyString(), eq("Моя категория"), anyList())).thenReturn(okResult());

        handler.handle(2L, "/new");
        handler.handle(2L, "идея");
        handler.handle(2L, "Моя категория");
        handler.handle(2L, "пример");
        handler.handle(2L, "/done");

        verify(pipeline).run(eq(2L), eq("идея"), eq("Моя категория"), eq(List.of("пример")));
    }

    @Test
    void fewerThanThreeExamplesWarnsButProceeds() {
        driveToExamples(3L);
        when(pipeline.run(anyLong(), anyString(), anyString(), anyList())).thenReturn(okResult());

        handler.handle(3L, "пример 1");
        List<String> replies = handler.handle(3L, "/done");

        assertThat(replies.get(0)).isEqualTo(BotReplies.FEW_EXAMPLES_WARNING);
    }

    @Test
    void doneWithoutExamplesAsksForThem() {
        driveToExamples(4L);

        List<String> replies = handler.handle(4L, "/done");

        assertThat(replies).containsExactly(BotReplies.NEED_EXAMPLES);
    }

    @Test
    void sixthExampleIsRejected() {
        driveToExamples(5L);
        for (int i = 1; i <= 5; i++) {
            handler.handle(5L, "пример " + i);
        }

        List<String> replies = handler.handle(5L, "лишний пример");

        assertThat(replies).containsExactly(BotReplies.EXAMPLES_LIMIT);
    }

    @Test
    void pipelineFailureKeepsStateAndExamples() {
        driveToExamples(6L);
        handler.handle(6L, "пример 1");
        when(pipeline.run(anyLong(), anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("api down"))
                .thenReturn(okResult());

        List<String> failed = handler.handle(6L, "/done");
        List<String> retried = handler.handle(6L, "/done");

        assertThat(failed).contains(BotReplies.LLM_ERROR);
        assertThat(String.join("\n", retried)).contains("Ноутбук Dell");
    }

    @Test
    void classifyFailureKeepsAwaitingIdea() {
        when(pipeline.classifyCategory("идея"))
                .thenThrow(new RuntimeException("api down"))
                .thenReturn("Категория");

        handler.handle(7L, "/new");
        List<String> failed = handler.handle(7L, "идея");
        List<String> retried = handler.handle(7L, "идея");

        assertThat(failed).containsExactly(BotReplies.LLM_ERROR);
        assertThat(retried.get(0)).contains("Категория");
    }

    @Test
    void publishedMarksLatestCreatedCase() {
        ListingCase created = new ListingCase();
        created.setGeneratedTitle("Ноутбук Dell");
        created.setStatus(ListingStatus.CREATED);
        when(repository.findFirstByChatIdAndStatusOrderByCreatedAtDesc(8L, ListingStatus.CREATED))
                .thenReturn(Optional.of(created));
        when(repository.save(any(ListingCase.class))).thenAnswer(inv -> inv.getArgument(0));

        List<String> replies = handler.handle(8L, "/published");

        assertThat(created.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
        assertThat(replies.get(0)).contains("Ноутбук Dell");
    }

    @Test
    void publishedWithoutCreatedCaseExplains() {
        when(repository.findFirstByChatIdAndStatusOrderByCreatedAtDesc(9L, ListingStatus.CREATED))
                .thenReturn(Optional.empty());

        assertThat(handler.handle(9L, "/published")).containsExactly(BotReplies.NOTHING_TO_PUBLISH);
    }

    @Test
    void unknownTextInIdleShowsHint() {
        assertThat(handler.handle(10L, "привет")).containsExactly(BotReplies.HINT);
    }

    @Test
    void cancelResetsFlow() {
        driveToExamples(11L);

        List<String> replies = handler.handle(11L, "/cancel");

        assertThat(replies).containsExactly(BotReplies.CANCELLED);
        assertThat(handler.handle(11L, "просто текст")).containsExactly(BotReplies.HINT);
    }
}
