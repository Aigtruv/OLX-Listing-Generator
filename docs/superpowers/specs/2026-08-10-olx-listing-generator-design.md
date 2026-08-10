# OLX Listing Generator — MVP Design

Date: 2026-08-10
Status: Approved by user (this session)
Project: ai-agent-publisher (Spring Boot 4.1, Java 25, Maven)

## Purpose

A Telegram bot that turns a product idea ("продаю ноутбуки") into a ready-to-publish
OLX.kz listing. The user supplies 3–5 example listings from the target category; the
agent analyzes why they succeed, then generates an original listing that follows the
winning pattern without resembling the examples. The user publishes on OLX manually.

This is sub-project 1 of a larger system. Later sub-projects (each with its own spec):
publisher automation, lead-qualification bots, sales funnel dashboards.

## Goals

- Idea → confirmed OLX.kz category → analysis of pasted examples → generated listing
  (title, description, price recommendation, photo checklist), all in Russian.
- Persist every case with a funnel status so later sub-projects can build on the data.
- Zero scraping, zero OLX automation: the user copies examples in and publishes out.

## Non-goals (explicitly deferred)

- Scraping or crawling OLX; fetching user-provided URLs.
- Auto-publishing to OLX in any form.
- Qualification bots, sales funnel logic beyond the status enum, dashboards.
- View boosting ("hot/cold" promotion mechanics).

## User flow (Telegram, Russian)

1. `/new` → bot asks for the idea.
2. User sends idea text. Bot calls Claude to classify it into an OLX.kz category and
   replies "Категория: Электроника → Ноутбуки — верно?" with confirm/correct options.
3. Bot asks the user to paste 3–5 example listings from that category (plain text,
   one or several messages; `/done` ends collection). Accepted range is 1–5; below
   3 the bot warns that analysis quality drops but proceeds.
4. Pipeline runs. Bot replies with:
   - **Analysis**: per example — what makes it work (title hooks, description
     structure, price positioning, photo count, trust signals), plus a summary of
     the category's winning template.
   - **Generated listing**: title, description, recommended price range, photo
     checklist. Formatted for copy-paste into OLX.
5. Similarity self-check runs before the reply: if the generated text overlaps an
   example too closely, it is regenerated once with an explicit "diverge" instruction.
6. User publishes manually on OLX, then sends `/published`. Status moves
   CREATED → PUBLISHED. `/status` shows current cases; `/cancel` aborts a flow.

## Architecture

Single Spring Boot application, four components:

### TelegramAdapter
- Purpose: all Telegram I/O; per-chat conversation state machine.
- Owns: conversation state (IDLE → AWAITING_IDEA → AWAITING_CATEGORY_CONFIRM →
  COLLECTING_EXAMPLES → GENERATING → DONE), command parsing.
- Does NOT own: any LLM logic or persistence beyond conversation state.
- Interface: Telegram long polling (telegrambots library); delegates to ListingPipeline.
- Dependencies: bot token via `TELEGRAM_BOT_TOKEN` env var (user creates the bot
  via BotFather and supplies the token).

### ListingPipeline
- Purpose: orchestrates the three LLM stages.
- Stages: `classifyCategory(idea)` → `analyzeExamples(category, examples)` →
  `generateListing(category, analysis, examples)` + similarity check.
- Similarity check: prompt-level self-check plus a programmatic n-gram overlap guard
  (reject if any 8-word shingle from an example appears verbatim in the output);
  one regeneration attempt, then deliver with a warning note.
- Does NOT own: Telegram formatting, HTTP details of the Anthropic API.

### AnthropicClient
- Purpose: thin wrapper over the Claude API (Messages API).
- Retries: 3 attempts, exponential backoff, on 429/5xx/timeouts.
- SDK: official Anthropic Java SDK (`com.anthropic:anthropic-java`).
- Model: `claude-opus-5` by default, configurable via `application.properties`
  (`app.anthropic.model`); API key via `ANTHROPIC_API_KEY` env var.

### ListingRepository
- Purpose: persistence via Spring Data JPA over file-based H2.
- H2 file mode keeps setup at zero for a single user; the schema is plain JPA so a
  later swap to PostgreSQL (when dashboards arrive) is a config change plus migration.

## Data model

One aggregate, `ListingCase`:

| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| chatId | long | Telegram chat |
| ideaText | String | raw user input |
| category | String | confirmed OLX.kz category path |
| examples | List<ExampleListing> | pasted texts, order preserved |
| analysisSummary | String | category winning-template summary |
| generatedTitle | String | |
| generatedDescription | String | |
| priceAdvice | String | recommended range + reasoning |
| status | enum CREATED / PUBLISHED / HOT / COLD | HOT/COLD unused in MVP, reserved for the funnel sub-project |
| createdAt / updatedAt | timestamps | |

`ExampleListing`: id, rawText, perListingAnalysis (String), position (int).

Conversation state lives in memory keyed by chatId with a 24h TTL; a restart drops
in-flight conversations (acceptable for MVP, cases already persisted are unaffected).

## Error handling

- Claude API failure after retries → Russian-language "попробуйте позже" message;
  conversation state preserved so the user can retry the step, not the whole flow.
- Unparseable / empty example paste → bot explains what it expects and re-prompts.
- Unknown command or message out of sequence → gentle hint with current step.
- Telegram polling errors → logged, polling restarts; no user-visible crash.

## Testing

- Unit: conversation state machine transitions; prompt builders; n-gram similarity
  guard (known-overlap and known-clean fixtures).
- Integration: full `/new → /done → result` flow with a mocked AnthropicClient and
  in-memory H2; asserts persisted ListingCase and reply contents.
- No live-API calls in CI. TDD per superpowers workflow during implementation.

## Conventions

Per repository CLAUDE.md: use `List` instead of raw arrays everywhere; use Apache
Commons `StringUtils` for string checks/manipulation (add `commons-lang3` dependency).
All user-facing bot text in Russian; code and comments in English.

## Configuration

| Key | Source | Notes |
|---|---|---|
| TELEGRAM_BOT_TOKEN | env | from BotFather |
| ANTHROPIC_API_KEY | env | user's key |
| app.anthropic.model | application.properties | model id, overridable |
| app.session.ttl | application.properties | default 24h |

## Compliance notes

- No OLX scraping or automation: the user is the only actor touching OLX.
- Generated listings must describe the real item; the agent prompts for truthful
  inputs (condition, specs) rather than inventing specifics.
