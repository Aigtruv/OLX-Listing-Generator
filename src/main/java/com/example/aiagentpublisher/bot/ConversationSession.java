package com.example.aiagentpublisher.bot;

import com.example.aiagentpublisher.sourcing.SourcingOffer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ConversationSession {

    private final long chatId;
    private ConversationState state = ConversationState.IDLE;
    private String ideaText;
    private String suggestedCategory;
    private String category;
    private final List<String> examples = new ArrayList<>();
    private final List<SourcingOffer> sourcingPicks = new ArrayList<>();
    private boolean awaitingPasteFallback;
    private Instant lastActivity;

    public ConversationSession(long chatId, Instant now) {
        this.chatId = chatId;
        this.lastActivity = now;
    }

    public void touch(Instant now) {
        this.lastActivity = now;
    }

    public long getChatId() {
        return chatId;
    }

    public ConversationState getState() {
        return state;
    }

    public void setState(ConversationState state) {
        this.state = state;
    }

    public String getIdeaText() {
        return ideaText;
    }

    public void setIdeaText(String ideaText) {
        this.ideaText = ideaText;
    }

    public String getSuggestedCategory() {
        return suggestedCategory;
    }

    public void setSuggestedCategory(String suggestedCategory) {
        this.suggestedCategory = suggestedCategory;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getExamples() {
        return examples;
    }

    public List<SourcingOffer> getSourcingPicks() {
        return sourcingPicks;
    }

    public boolean isAwaitingPasteFallback() {
        return awaitingPasteFallback;
    }

    public void setAwaitingPasteFallback(boolean awaitingPasteFallback) {
        this.awaitingPasteFallback = awaitingPasteFallback;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }
}
