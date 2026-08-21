package com.example.aiagentpublisher.bot;

import com.example.aiagentpublisher.domain.ExampleListing;
import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.olx.OlxListingFetcher;
import com.example.aiagentpublisher.pipeline.ListingPipeline;
import com.example.aiagentpublisher.pipeline.PipelineResult;
import com.example.aiagentpublisher.sourcing.SourcingOffer;
import com.example.aiagentpublisher.sourcing.SourcingScout;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationHandler {

    private static final Logger log = LoggerFactory.getLogger(ConversationHandler.class);
    private static final int MAX_EXAMPLES = 5;
    private static final int RECOMMENDED_EXAMPLES = 3;

    private final ConversationSessionStore sessions;
    private final ListingPipeline pipeline;
    private final ListingCaseRepository repository;
    private final OlxListingFetcher olxListingFetcher;
    private final SourcingScout sourcingScout;

    public ConversationHandler(ConversationSessionStore sessions, ListingPipeline pipeline,
                               ListingCaseRepository repository, OlxListingFetcher olxListingFetcher,
                               SourcingScout sourcingScout) {
        this.sessions = sessions;
        this.pipeline = pipeline;
        this.repository = repository;
        this.olxListingFetcher = olxListingFetcher;
        this.sourcingScout = sourcingScout;
    }

    public List<String> handle(long chatId, String rawText) {
        String text = StringUtils.trim(rawText);
        if (StringUtils.isBlank(text)) {
            return List.of(BotReplies.HINT);
        }
        return switch (text) {
            case "/new" -> startNew(chatId);
            case "/cancel" -> cancel(chatId);
            case "/status" -> status(chatId);
            case "/published" -> markPublished(chatId);
            case "/done" -> finishExamples(chatId);
            default -> handleText(sessions.get(chatId), text);
        };
    }

    private List<String> startNew(long chatId) {
        sessions.reset(chatId);
        sessions.get(chatId).setState(ConversationState.AWAITING_IDEA);
        return List.of(BotReplies.ASK_IDEA);
    }

    private List<String> cancel(long chatId) {
        sessions.reset(chatId);
        return List.of(BotReplies.CANCELLED);
    }

    private List<String> status(long chatId) {
        List<ListingCase> cases = repository.findByChatIdOrderByCreatedAtDesc(chatId);
        if (cases.isEmpty()) {
            return List.of(BotReplies.NO_CASES);
        }
        StringBuilder sb = new StringBuilder("Ваши объявления:\n");
        cases.stream().limit(5).forEach(c -> sb.append("• ")
                .append(StringUtils.defaultIfBlank(c.getGeneratedTitle(), c.getIdeaText()))
                .append(" — ").append(c.getStatus()).append("\n"));
        ListingCase latest = cases.get(0);
        if (StringUtils.isNotBlank(latest.getGeneratedTitle())
                || StringUtils.isNotBlank(latest.getGeneratedDescription())) {
            sb.append("\nПоследнее объявление:\n");
            if (StringUtils.isNotBlank(latest.getGeneratedTitle())) {
                sb.append("📝 Заголовок:\n").append(latest.getGeneratedTitle()).append("\n");
            }
            if (StringUtils.isNotBlank(latest.getGeneratedDescription())) {
                sb.append("📄 Описание:\n").append(latest.getGeneratedDescription()).append("\n");
            }
            if (StringUtils.isNotBlank(latest.getPriceAdvice())) {
                sb.append("💰 Цена: ").append(latest.getPriceAdvice()).append("\n");
            }
        }
        return List.of(sb.toString());
    }

    private List<String> markPublished(long chatId) {
        return repository.findFirstByChatIdAndStatusOrderByCreatedAtDesc(chatId, ListingStatus.CREATED)
                .map(c -> {
                    c.setStatus(ListingStatus.PUBLISHED);
                    repository.save(c);
                    return List.of(BotReplies.PUBLISHED_OK.formatted(c.getGeneratedTitle()));
                })
                .orElse(List.of(BotReplies.NOTHING_TO_PUBLISH));
    }

    private List<String> handleText(ConversationSession session, String text) {
        return switch (session.getState()) {
            case AWAITING_IDEA -> captureIdea(session, text);
            case AWAITING_SOURCING_PICK -> pickSourcing(session, text);
            case AWAITING_CATEGORY_CONFIRM -> confirmCategory(session, text);
            case COLLECTING_EXAMPLES -> collectExample(session, text);
            case IDLE -> List.of(BotReplies.HINT);
        };
    }

    private List<String> captureIdea(ConversationSession session, String idea) {
        session.setIdeaText(idea);
        session.getSourcingPicks().clear();
        try {
            List<SourcingOffer> offers = sourcingScout.search(idea);
            if (offers != null) {
                session.getSourcingPicks().addAll(offers);
            }
        } catch (RuntimeException e) {
            log.error("Sourcing failed for chat {}", session.getChatId(), e);
            session.setState(ConversationState.AWAITING_SOURCING_PICK);
            return List.of(BotReplies.SOURCING_NONE);
        }
        session.setState(ConversationState.AWAITING_SOURCING_PICK);
        if (session.getSourcingPicks().isEmpty()) {
            return List.of(BotReplies.SOURCING_NONE);
        }
        return List.of(formatSourcingPicks(session.getSourcingPicks()));
    }

    private List<String> pickSourcing(ConversationSession session, String text) {
        if (StringUtils.equalsIgnoreCase(text, "пропустить")) {
            return classifyAndAskCategory(session, session.getIdeaText());
        }
        int index = parsePick(text);
        if (index < 0 || index >= session.getSourcingPicks().size()) {
            return List.of(BotReplies.SOURCING_BAD_PICK);
        }
        SourcingOffer offer = session.getSourcingPicks().get(index);
        String enriched = session.getIdeaText() + "\nЗакупка: " + offer.title() + " " + offer.url();
        session.setIdeaText(enriched);
        return classifyAndAskCategory(session, enriched);
    }

    private static int parsePick(String text) {
        if (StringUtils.equals(text, "1")) {
            return 0;
        }
        if (StringUtils.equals(text, "2")) {
            return 1;
        }
        if (StringUtils.equals(text, "3")) {
            return 2;
        }
        return -1;
    }

    private List<String> classifyAndAskCategory(ConversationSession session, String idea) {
        try {
            String category = pipeline.classifyCategory(idea);
            session.setSuggestedCategory(category);
            session.setState(ConversationState.AWAITING_CATEGORY_CONFIRM);
            return List.of(BotReplies.CATEGORY_CONFIRM.formatted(category));
        } catch (RuntimeException e) {
            log.error("Category classification failed for chat {}", session.getChatId(), e);
            return List.of(BotReplies.LLM_ERROR);
        }
    }

    private static String formatSourcingPicks(List<SourcingOffer> offers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < offers.size(); i++) {
            SourcingOffer offer = offers.get(i);
            sb.append(i + 1).append(". ").append(offer.title())
                    .append(" — ").append(offer.priceKzt()).append(" ₸\n")
                    .append(offer.url()).append("\n");
        }
        sb.append("\n").append(BotReplies.SOURCING_PICK);
        return sb.toString();
    }

    private List<String> confirmCategory(ConversationSession session, String text) {
        String category = StringUtils.equalsIgnoreCase(text, "да")
                ? session.getSuggestedCategory()
                : text;
        session.setCategory(category);
        session.setState(ConversationState.COLLECTING_EXAMPLES);
        persistDraft(session);
        return List.of(BotReplies.ASK_EXAMPLES);
    }

    private List<String> collectExample(ConversationSession session, String text) {
        if (session.getExamples().size() >= MAX_EXAMPLES) {
            return List.of(BotReplies.EXAMPLES_LIMIT);
        }
        if (olxListingFetcher.isListingUrl(text)) {
            return olxListingFetcher.fetch(text)
                    .map(listing -> acceptExample(session, listing.formatForPipeline()))
                    .orElseGet(() -> {
                        session.setAwaitingPasteFallback(true);
                        return List.of(BotReplies.OLX_FETCH_FAILED);
                    });
        }
        if (session.isAwaitingPasteFallback()) {
            return acceptExample(session, text);
        }
        return List.of(BotReplies.ASK_OLX_URL);
    }

    private List<String> acceptExample(ConversationSession session, String exampleText) {
        session.getExamples().add(exampleText);
        session.setAwaitingPasteFallback(false);
        persistDraft(session);
        return List.of(BotReplies.EXAMPLE_ACCEPTED.formatted(session.getExamples().size()));
    }

    private List<String> finishExamples(long chatId) {
        ConversationSession session = sessions.get(chatId);
        if (session.getState() != ConversationState.COLLECTING_EXAMPLES) {
            return List.of(BotReplies.HINT);
        }
        if (session.getExamples().isEmpty()) {
            return List.of(BotReplies.NEED_EXAMPLES);
        }
        List<String> replies = new ArrayList<>();
        if (session.getExamples().size() < RECOMMENDED_EXAMPLES) {
            replies.add(BotReplies.FEW_EXAMPLES_WARNING);
        }
        try {
            PipelineResult result = pipeline.run(chatId, session.getIdeaText(),
                    session.getCategory(), List.copyOf(session.getExamples()));
            sessions.reset(chatId);
            replies.addAll(formatResult(result));
        } catch (RuntimeException e) {
            log.error("Listing generation failed for chat {}", chatId, e);
            replies.add(BotReplies.LLM_ERROR);
        }
        return replies;
    }

    private void persistDraft(ConversationSession session) {
        ListingCase draft = repository
                .findFirstByChatIdAndStatusOrderByCreatedAtDesc(session.getChatId(), ListingStatus.DRAFT)
                .orElseGet(ListingCase::new);
        draft.setChatId(session.getChatId());
        draft.setIdeaText(session.getIdeaText());
        draft.setCategory(session.getCategory());
        draft.setStatus(ListingStatus.DRAFT);
        draft.getExamples().clear();
        for (String text : session.getExamples()) {
            ExampleListing example = new ExampleListing();
            example.setRawText(text);
            draft.getExamples().add(example);
        }
        repository.save(draft);
    }

    private List<String> formatResult(PipelineResult result) {
        List<String> replies = new ArrayList<>();

        StringBuilder analysis = new StringBuilder("📊 Анализ примеров:\n");
        List<String> perExample = result.analysis().perExampleAnalysis() == null
                ? List.of()
                : result.analysis().perExampleAnalysis();
        for (int i = 0; i < perExample.size(); i++) {
            analysis.append(i + 1).append(". ").append(perExample.get(i)).append("\n");
        }
        analysis.append("\n🏆 Шаблон успеха:\n")
                .append(StringUtils.defaultString(result.analysis().winningTemplate()));
        replies.add(analysis.toString());

        GeneratedListing listing = result.listing();
        replies.add("📝 Заголовок:\n" + StringUtils.defaultString(listing.title()));
        replies.add("📄 Описание:\n" + StringUtils.defaultString(listing.description()));

        StringBuilder tail = new StringBuilder("💰 Цена: ")
                .append(StringUtils.defaultString(listing.priceAdvice()));
        tail.append("\n\n📷 Фото-чеклист:\n");
        List<String> shots = listing.photoChecklist() == null ? List.of() : listing.photoChecklist();
        for (String shot : shots) {
            tail.append("• ").append(shot).append("\n");
        }
        tail.append("\nОпубликуйте на OLX и отправьте /published.");
        if (result.similarityWarning()) {
            tail.append("\n\n").append(BotReplies.SIMILARITY_WARNING);
        }
        replies.add(tail.toString());
        return replies;
    }
}
