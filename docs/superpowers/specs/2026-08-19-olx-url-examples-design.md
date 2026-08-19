# OLX Listing URL Examples — Design

Date: 2026-08-19
Status: Approved by user (this session)
Project: ai-agent-publisher

## Purpose

When collecting competitor examples after category confirm, the Telegram bot
asks for olx.kz listing URLs instead of pasted ad text. It fetches each page
and extracts title, price, and description for the existing DeepSeek analysis
pipeline.

## Goals

- Idea and category steps stay free-text (unchanged).
- Example step: 3–5 `https://www.olx.kz/d/obyavlenie/…` links, one per
  message, then `/done`. Also accept `https://olx.kz/…` (no `www`) and
  optional query strings.
- HTTP GET the page (browser User-Agent). Extract title, price, description.
  Store `URL + extracted fields` in existing `ExampleListing.rawText`.
- Invalid URL: Russian re-prompt; do not count as an example.
- Fetch/extract failure: Russian message offering another link **or** paste
  the ad text. The next non-URL message in that session is stored as pasted
  text (fallback). After a successful example (link or paste), fallback
  mode clears.
- Bot never crashes on OLX HTTP errors. No live OLX calls in tests.

## Non-goals

- Playwright / headless browser.
- Sending the URL to DeepSeek without fetching.
- Other marketplaces, photos, login, cookies, changing idea/category.
- New database columns or pipeline/LLM contract changes.

## Approach

New `OlxListingFetcher` (RestClient, timeouts 5s connect / 10s read, 3
retries on `RestClientException`):

- `boolean isListingUrl(String)` — Apache `StringUtils`; host `olx.kz` or
  `www.olx.kz`; path starts with `/d/obyavlenie/`.
- `OlxListing fetch(String url)` — GET, parse HTML for title/price/description.
  Prefer Open Graph / visible heading + price + description block; strip
  tags. If extract is blank, treat as failure.

`ConversationHandler` during `COLLECTING_EXAMPLES`:

1. `/done` and limits unchanged (1–5 examples).
2. If `isListingUrl`: fetch; on success append formatted text; on failure
   set `awaitingPasteFallback` and reply with the failure copy.
3. Else if `awaitingPasteFallback` and text is non-blank: store the paste,
   clear the flag, count as an example.
4. Else: invalid-link re-prompt.

Russian copy (buyer-facing):

- Ask: «Теперь пришлите 3–5 ссылок на объявления olx.kz (каждое отдельным
  сообщением), например:
  https://www.olx.kz/d/obyavlenie/noutbuk-dell-inspiron-3162-IDqzxSX.html
  Когда закончите — /done.»
- Bad URL: «Пришлите ссылку на объявление olx.kz, например:
  https://www.olx.kz/d/obyavlenie/…»
- Fetch fail: «Не удалось открыть объявление. Пришлите другую ссылку или
  вставьте текст объявления.»
- Accepted: keep «Пример N принят. Ещё один или /done.»

App config: `olxRestClient` bean, `app.olx.base` not required (absolute
URLs). No extra env vars.

## Testing

`MockRestServiceServer` + HTML fixture (no live olx.kz):

- Extract title/price/description from fixture.
- Handler: non-olx message → re-prompt, zero examples.
- 404 → fallback prompt; following paste is stored.
- Existing pipeline tests still pass with example **text** (fetcher is not
  used inside `ListingPipeline`).
