package com.example.aiagentpublisher.bot;

import com.example.aiagentpublisher.domain.ExampleListing;
import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;
import com.example.aiagentpublisher.olx.OlxListing;
import com.example.aiagentpublisher.olx.OlxListingFetcher;
import com.example.aiagentpublisher.pipeline.ListingPipeline;
import com.example.aiagentpublisher.pipeline.PipelineResult;
import com.example.aiagentpublisher.sourcing.SourcingOffer;
import com.example.aiagentpublisher.sourcing.SourcingScout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationHandlerTest {

    @Mock
    private ListingPipeline pipeline;

    @Mock
    private ListingCaseRepository repository;

    @Mock
    private OlxListingFetcher olxListingFetcher;

    @Mock
    private SourcingScout sourcingScout;

    private ConversationHandler handler;

    private static PipelineResult okResult() {
        return new PipelineResult(
                new ListingAnalysis(List.of("анализ 1", "анализ 2", "анализ 3"), "шаблон успеха"),
                new GeneratedListing("Ноутбук Dell", "Описание без совпадений.", "150 000 тг",
                        List.of("фото экрана", "фото клавиатуры")),
                false);
    }

    private static String listingUrl(int n) {
        return "https://www.olx.kz/d/obyavlenie/p" + n + ".html";
    }

    private static String formattedExample(int n) {
        String url = listingUrl(n);
        return new OlxListing(url, "t", "1 тг", url).formatForPipeline();
    }

    @BeforeEach
    void setUp() {
        ConversationSessionStore store =
                new ConversationSessionStore(Clock.systemUTC(), Duration.ofHours(24));
        handler = new ConversationHandler(store, pipeline, repository, olxListingFetcher, sourcingScout);
        lenient().when(sourcingScout.search(anyString())).thenReturn(List.of());
        lenient().when(olxListingFetcher.isListingUrl(anyString())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            return text != null && text.contains("olx.kz");
        });
        lenient().when(olxListingFetcher.fetch(anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return Optional.of(new OlxListing(url, "t", "1 тг", url));
        });
        lenient().when(repository.findFirstByChatIdAndStatusOrderByCreatedAtDesc(anyLong(), eq(ListingStatus.DRAFT)))
                .thenReturn(Optional.empty());
        lenient().when(repository.save(any(ListingCase.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void driveToExamples(long chatId) {
        when(pipeline.classifyCategory("продаю ноутбуки")).thenReturn("Электроника → Ноутбуки");
        handler.handle(chatId, "/new");
        handler.handle(chatId, "продаю ноутбуки");
        handler.handle(chatId, "пропустить");
        handler.handle(chatId, "да");
    }

    @Test
    void fullHappyFlowProducesListingMessages() {
        driveToExamples(1L);
        when(pipeline.run(eq(1L), eq("продаю ноутбуки"), eq("Электроника → Ноутбуки"),
                eq(List.of(formattedExample(1), formattedExample(2), formattedExample(3)))))
                .thenReturn(okResult());

        handler.handle(1L, listingUrl(1));
        handler.handle(1L, listingUrl(2));
        handler.handle(1L, listingUrl(3));
        List<String> replies = handler.handle(1L, "/done");

        String all = String.join("\n", replies);
        assertThat(all).contains("Ноутбук Dell").contains("шаблон успеха")
                .contains("150 000 тг").contains("фото экрана");
        assertThat(all).doesNotContain(BotReplies.SIMILARITY_WARNING);
    }

    @Test
    void categoryCorrectionUsesUserText() {
        when(pipeline.classifyCategory("идея")).thenReturn("Неверная категория");
        when(pipeline.run(anyLong(), anyString(), eq("Моя категория"),
                eq(List.of(formattedExample(1))))).thenReturn(okResult());

        handler.handle(2L, "/new");
        handler.handle(2L, "идея");
        handler.handle(2L, "пропустить");
        handler.handle(2L, "Моя категория");
        handler.handle(2L, listingUrl(1));
        handler.handle(2L, "/done");

        verify(pipeline).run(eq(2L), eq("идея"), eq("Моя категория"), eq(List.of(formattedExample(1))));
    }

    @Test
    void fewerThanThreeExamplesWarnsButProceeds() {
        driveToExamples(3L);
        when(pipeline.run(anyLong(), anyString(), anyString(), anyList())).thenReturn(okResult());

        handler.handle(3L, listingUrl(1));
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
            handler.handle(5L, listingUrl(i));
        }

        List<String> replies = handler.handle(5L, listingUrl(6));

        assertThat(replies).containsExactly(BotReplies.EXAMPLES_LIMIT);
    }

    @Test
    void persistsDraftAfterExampleAccepted() {
        driveToExamples(30L);
        handler.handle(30L, listingUrl(1));

        ArgumentCaptor<ListingCase> captor = ArgumentCaptor.forClass(ListingCase.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        ListingCase draft = captor.getValue();
        assertThat(draft.getStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(draft.getChatId()).isEqualTo(30L);
        assertThat(draft.getIdeaText()).isEqualTo("продаю ноутбуки");
        assertThat(draft.getCategory()).isEqualTo("Электроника → Ноутбуки");
        assertThat(draft.getExamples()).extracting(ExampleListing::getRawText)
                .containsExactly(formattedExample(1));
    }

    @Test
    void formatsResultWhenPhotoChecklistIsNull() {
        driveToExamples(31L);
        handler.handle(31L, listingUrl(1));
        when(pipeline.run(anyLong(), anyString(), anyString(), anyList()))
                .thenReturn(new PipelineResult(
                        new ListingAnalysis(List.of("анализ"), "шаблон"),
                        new GeneratedListing("Ноутбук Dell", "Описание.", "150 000 тг", null),
                        false));

        List<String> replies = handler.handle(31L, "/done");

        String all = String.join("\n", replies);
        assertThat(all).contains("Ноутбук Dell").contains("Описание.").contains("150 000 тг");
        assertThat(all).doesNotContain(BotReplies.LLM_ERROR);
    }

    @Test
    void statusIncludesLatestGeneratedListing() {
        ListingCase created = new ListingCase();
        created.setGeneratedTitle("Ноутбук Dell");
        created.setGeneratedDescription("Описание.");
        created.setPriceAdvice("150 000 тг");
        created.setStatus(ListingStatus.CREATED);
        created.setIdeaText("продаю ноутбуки");
        when(repository.findByChatIdOrderByCreatedAtDesc(40L)).thenReturn(List.of(created));

        List<String> replies = handler.handle(40L, "/status");

        assertThat(replies.get(0)).contains("Ноутбук Dell").contains("Описание.").contains("150 000 тг");
    }

    @Test
    void pipelineFailureKeepsStateAndExamples() {
        driveToExamples(6L);
        handler.handle(6L, listingUrl(1));
        when(pipeline.run(anyLong(), anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("api down"))
                .thenReturn(okResult());

        List<String> failed = handler.handle(6L, "/done");
        List<String> retried = handler.handle(6L, "/done");

        assertThat(failed).contains(BotReplies.LLM_ERROR);
        assertThat(String.join("\n", retried)).contains("Ноутбук Dell");
    }

    @Test
    void classifyFailureKeepsSourcingPick() {
        when(pipeline.classifyCategory("идея"))
                .thenThrow(new RuntimeException("api down"))
                .thenReturn("Категория");

        handler.handle(7L, "/new");
        handler.handle(7L, "идея");
        List<String> failed = handler.handle(7L, "пропустить");
        List<String> retried = handler.handle(7L, "пропустить");

        assertThat(failed).containsExactly(BotReplies.LLM_ERROR);
        assertThat(retried.get(0)).contains("Категория");
    }

    @Test
    void ideaShowsTopOffersThenPickEnrichesIdea() {
        when(sourcingScout.search("хочу продавать gps трекеры")).thenReturn(List.of(
                new SourcingOffer("ali", "AliExpress", "GPS A", "https://a", 1000),
                new SourcingOffer("ebay", "eBay.de", "GPS B", "https://b", 2000),
                new SourcingOffer("amz", "Amazon.de", "GPS C", "https://c", 3000)));
        when(pipeline.classifyCategory(contains("GPS B"))).thenReturn("Электроника → GPS");

        handler.handle(50L, "/new");
        List<String> found = handler.handle(50L, "хочу продавать gps трекеры");
        assertThat(String.join("\n", found)).contains("GPS A").contains("1000 ₸").contains(BotReplies.SOURCING_PICK);

        List<String> afterPick = handler.handle(50L, "2");
        assertThat(afterPick.get(0)).contains("Электроника → GPS");
        verify(pipeline).classifyCategory(contains("https://b"));
    }

    @Test
    void skipSourcingClassifiesOriginalIdea() {
        when(pipeline.classifyCategory("продаю ноутбуки")).thenReturn("Электроника → Ноутбуки");
        handler.handle(51L, "/new");
        handler.handle(51L, "продаю ноутбуки");
        List<String> replies = handler.handle(51L, "пропустить");
        assertThat(replies.get(0)).contains("Электроника → Ноутбуки");
        verify(pipeline).classifyCategory("продаю ноутбуки");
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

    @Test
    void nonOlxTextWhileCollectingAsksForUrl() {
        driveToExamples(20L);

        assertThat(handler.handle(20L, "просто текст")).containsExactly(BotReplies.ASK_OLX_URL);
    }

    @Test
    void fetchFailureThenPasteIsAccepted() {
        driveToExamples(21L);
        String url = listingUrl(1);
        when(olxListingFetcher.fetch(url)).thenReturn(Optional.empty());
        when(pipeline.run(anyLong(), anyString(), anyString(), eq(List.of("вставленный текст"))))
                .thenReturn(okResult());

        assertThat(handler.handle(21L, url)).containsExactly(BotReplies.OLX_FETCH_FAILED);
        handler.handle(21L, "вставленный текст");
        List<String> replies = handler.handle(21L, "/done");
        assertThat(String.join("\n", replies)).contains("Ноутбук Dell");
    }
}
