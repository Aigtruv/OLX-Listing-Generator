# WhatsApp Lead Qualification Bot — Design

Date: 2026-08-13
Status: Approved by user (this session)
Project: ai-agent-publisher (Spring Boot 4.1, Java 25, Maven)

This is sub-project 2 of the larger system. Sub-project 1 (OLX listing generator
Telegram bot) is already implemented. Later sub-projects: sales funnel rules
(HOT/COLD), dashboards.

## Purpose

Buyers who see an OLX.kz listing message one shared WhatsApp Business number.
A bot identifies which published listing they mean, asks three fixed screening
questions in Russian, stores the answers, and pings the seller in the existing
Telegram listing bot. The seller decides later who to contact. The bot never
sends the seller’s phone number or Telegram username to the buyer.

## Goals

- One shared WhatsApp Cloud API number for all listings.
- First step: numbered menu of currently `PUBLISHED` `ListingCase` rows.
- Fixed script (one question at a time): city, budget, when they want to buy.
- Persist a `Lead` linked to the chosen listing.
- One Telegram push to the seller (`ListingCase.chatId`) with listing title,
  buyer WhatsApp number, and the three answers.
- App starts with WhatsApp env vars unset (webhook/client no-op), same as
  Telegram/Anthropic today.

## Non-goals (explicitly deferred)

- Auto-sending the seller’s contact to the buyer.
- `/leads` list, HOT/COLD rules, dashboards.
- Per-listing or per-product phone numbers.
- WhatsApp media, interactive buttons/templates, voice, or WhatsApp Groups.
- Unofficial WhatsApp Web / Baileys.
- OLX chat automation or scraping.
- LLM on the buyer path (no Claude calls in this slice).
- Multi-seller tenancy (one seller / one Telegram chat per listing is enough).

## Buyer flow (WhatsApp, Russian)

1. Buyer sends any text to the shared number.
2. Bot replies with a numbered list of currently `PUBLISHED` listings (title,
   falling back to idea text), most recent first, **maximum 10**. If none:
   «Сейчас ничего не продаётся.» Session stays idle.
3. Buyer replies with a number. Invalid / out of range → «Отправьте номер из
   списка.» Stay on this step.
4. Ask, one at a time, waiting for a non-blank text answer:
   - «В каком вы городе?»
   - «Какой у вас бюджет?»
   - «Когда готовы купить?»
5. Persist `Lead` with status `NEW`. Reply:
   «Спасибо, продавец свяжется с вами.»
6. Send one Telegram message to `listingCase.chatId`:
   listing title, buyer WhatsApp id/phone, city, budget, timeframe.

Non-text inbound (stickers, images, reactions): ignore, no state change.
Buyer may send `стоп` (case-insensitive, trimmed) at any step after the first
inbound: drop the session, do not save a lead, reply «Ок, остановил.»

Blank answers: re-prompt the same question.

## Architecture

Single Spring Boot application. New package `com.example.aiagentpublisher.leads`.
Reuse H2, `ListingCaseRepository`, and Telegram outbound.

### WhatsAppAdapter
- Purpose: all WhatsApp Cloud API I/O.
- Owns: webhook verification (GET), inbound parse (POST), outbound send text,
  inbound message-id idempotency.
- Does NOT own: screening logic or persistence of leads.
- Interface: Spring MVC controller + thin HTTP client to Graph API
  `/{phone-number-id}/messages`. No extra WhatsApp SDK; use Spring `RestClient`.
- Dependencies: `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`,
  `WHATSAPP_VERIFY_TOKEN` env vars. Optional `WHATSAPP_APP_SECRET` for
  `X-Hub-Signature-256` validation when set.

### QualificationHandler
- Purpose: per-buyer state machine; no WhatsApp or Telegram types.
- Interface: `List<String> handle(String buyerWaId, String rawText)` plus a
  side effect: save lead and call `SellerNotifier` when the script completes.
- States: `IDLE` → `PICKING_LISTING` → `ASKING_CITY` → `ASKING_BUDGET` →
  `ASKING_TIMEFRAME` → (complete, reset to idle).
- Does NOT own: HTTP, Graph API payloads, Telegram formatting.

### QualificationSessionStore
- Purpose: in-memory session per `buyerWaId`, 24h TTL, same pattern as
  `ConversationSessionStore` (inject the existing `Clock` bean).
- Restart drops in-flight buyer chats; saved leads are unaffected.

### LeadRepository
- Purpose: persist `Lead` via Spring Data JPA on the existing file H2.

### SellerNotifier
- Purpose: send one Telegram text to a chat id.
- Requires `TelegramClient` as a Spring bean. Refactor `TelegramBotStarter`
  to consume that bean instead of constructing `OkHttpTelegramClient` locally.
  If `TELEGRAM_BOT_TOKEN` is blank, notifier logs and no-ops (leads still save).

### ListingCaseRepository addition
- `List<ListingCase> findByStatusOrderByCreatedAtDesc(ListingStatus status)`
  for the published-listing menu.

## Data model

New entity `Lead`:

| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| listingCase | ManyToOne ListingCase | required |
| buyerWaId | String | WhatsApp Cloud API `wa_id` (phone) |
| city | String | answer 1 |
| budget | String | answer 2 |
| timeframe | String | answer 3 |
| status | enum LeadStatus { NEW } | further values reserved for funnel sub-project |
| createdAt | Instant | |

No update of `ListingCase.status` in this slice (stays `PUBLISHED`).

Inbound WhatsApp message ids: in-memory set with TTL (same 24h). Duplicate
webhook deliveries are ignored.

## Error handling

- Graph API send failure: 3 attempts with backoff; then log. Do not crash.
  Buyer may see silence for that one reply.
- Webhook verify token mismatch: HTTP 403, empty body.
- Signature mismatch when `WHATSAPP_APP_SECRET` is set: HTTP 403.
- Telegram ping failure after `Lead` is saved: log; lead remains `NEW`.
- Unknown webhook object types: HTTP 200 (ack) and ignore.
- Missing WhatsApp env vars: controller still maps; GET verify fails closed;
  POST acks 200 and no-ops so Meta retries do not crash a token-less app.

## Testing

- Unit: QualificationHandler — empty published list; pick listing; happy path
  of three answers → lead saved and notifier invoked; `стоп` clears session
  with no save; invalid menu index stays on pick-listing; blank re-prompts.
- Unit: WhatsAppAdapter — non-text ignored; GET verify success/fail;
  duplicate message id ignored.
- Integration: `@SpringBootTest` with mocked Graph API and `SellerNotifier`
  (or mocked `TelegramClient`); drive one full buyer flow; assert `Lead` in H2
  and notifier arguments.
- No live Meta or Telegram calls in CI. TDD during implementation.

## Conventions

Per repository CLAUDE.md: `List` instead of raw arrays; Apache Commons
`StringUtils` for string checks/manipulation. Buyer-facing WhatsApp text in
Russian; seller Telegram ping in Russian; code and comments in English.

## Configuration

| Key | Source | Notes |
|---|---|---|
| WHATSAPP_TOKEN | env | Cloud API permanent or temporary token |
| WHATSAPP_PHONE_NUMBER_ID | env | Graph phone-number-id, not the display number |
| WHATSAPP_VERIFY_TOKEN | env | arbitrary string, must match Meta webhook config |
| WHATSAPP_APP_SECRET | env | optional; enables signature check |
| TELEGRAM_BOT_TOKEN | env | existing; required for seller pings |
| app.session.ttl | application.properties | reused for buyer sessions, default 24h |

Local Meta webhook: public HTTPS URL (ngrok or Cloudflare Tunnel) pointing at
`/webhooks/whatsapp`. Document the tunnel step in README; do not commit secrets.

## Compliance notes

- Official WhatsApp Cloud API only.
- Screening is data collection, not automated rejection.
- Seller remains the only person who contacts a buyer after review.
