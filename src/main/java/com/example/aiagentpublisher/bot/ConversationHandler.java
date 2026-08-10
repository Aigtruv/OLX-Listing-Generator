package com.example.aiagentpublisher.bot;

import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.pipeline.ListingPipeline;
import com.example.aiagentpublisher.pipeline.PipelineResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationHandler {

    private static final int MAX_EXAMPLES = 5;
    private static final int RECOMMENDED_EXAMPLES = 3;

    private final ConversationSessionStore sessions;
    private final ListingPipeline pipeline;
    private final ListingCaseRepository repository;

    public ConversationHandler(ConversationSessionStore sessions, ListingPipeline pipeline,
                               ListingCaseRepository repository) {
        this.sessions = sessions;
        this.pipeline = pipeline;
        this.repository = repository;
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
            case AWAITING_CATEGORY_CONFIRM -> confirmCategory(session, text);
            case COLLECTING_EXAMPLES -> collectExample(session, text);
            case IDLE -> List.of(BotReplies.HINT);
        };
    }

    private List<String> captureIdea(ConversationSession session, String idea) {
        try {
            String category = pipeline.classifyCategory(idea);
            session.setIdeaText(idea);
            session.setSuggestedCategory(category);
            session.setState(ConversationState.AWAITING_CATEGORY_CONFIRM);
            return List.of(BotReplies.CATEGORY_CONFIRM.formatted(category));
        } catch (RuntimeException e) {
            return List.of(BotReplies.LLM_ERROR);
        }
    }

    private List<String> confirmCategory(ConversationSession session, String text) {
        String category = StringUtils.equalsIgnoreCase(text, "да")
                ? session.getSuggestedCategory()
                : text;
        session.setCategory(category);
        session.setState(ConversationState.COLLECTING_EXAMPLES);
        return List.of(BotReplies.ASK_EXAMPLES);
    }

    private List<String> collectExample(ConversationSession session, String text) {
        if (session.getExamples().size() >= MAX_EXAMPLES) {
            return List.of(BotReplies.EXAMPLES_LIMIT);
        }
        session.getExamples().add(text);
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
            replies.add(BotReplies.LLM_ERROR);
        }
        return replies;
    }

    private List<String> formatResult(PipelineResult result) {
        List<String> replies = new ArrayList<>();

        StringBuilder analysis = new StringBuilder("📊 Анализ примеров:\n");
        List<String> perExample = result.analysis().perExampleAnalysis();
        for (int i = 0; i < perExample.size(); i++) {
            analysis.append(i + 1).append(". ").append(perExample.get(i)).append("\n");
        }
        analysis.append("\n🏆 Шаблон успеха:\n").append(result.analysis().winningTemplate());
        replies.add(analysis.toString());

        GeneratedListing listing = result.listing();
        replies.add("📝 Заголовок:\n" + listing.title());
        replies.add("📄 Описание:\n" + listing.description());

        StringBuilder tail = new StringBuilder("💰 Цена: ").append(listing.priceAdvice());
        tail.append("\n\n📷 Фото-чеклист:\n");
        for (String shot : listing.photoChecklist()) {
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
