# Wholesale Sourcing in /new — Design

Date: 2026-08-20
Status: Approved by user (this session)
Project: ai-agent-publisher

## Purpose

During `/new`, after the seller describes what they want to sell, the bot
looks up **1-piece buy prices** on a fixed allowlist of China and Europe
marketplaces, shows the **three cheapest in ₸**, and the seller picks one
before category confirm and OLX listing generation.

Example: «хочу продавать gps трекеры» → search AliExpress, Alibaba, 1688,
Amazon.de, eBay.de → top 3 cheapest 1-pc offers in tenge → reply `1` / `2`
/ `3` → existing category + OLX examples + `/done` flow.

## Goals

- Insert one step between idea capture and category confirm.
- Allowlist (config, add later without code if possible): AliExpress,
  Alibaba, 1688.com, Amazon.de, eBay.de.
- Rank by **1-piece price converted to KZT**. Display **only ₸** (no
  USD/EUR/CNY in the bot reply).
- HTTP GET public search pages and parse offers (same style as
  `OlxListingFetcher`). No invented prices.
- Failed or empty sites are skipped. Fewer than three live offers is OK;
  show what we have.
- User replies `1` / `2` / `3` to pick, or `пропустить` to continue with
  the original idea only.
- Chosen offer is folded into the idea text used for classify/generate
  (title + URL). No new database table.
- User-facing copy in Russian. Tests do not hit live marketplaces.

## Non-goals

- Official Amazon / eBay / AliExpress / 1688 APIs.
- Shipping, customs, VAT, MOQ > 1, seller ratings, “verified store” badges.
- Live FX APIs (use a config FX table).
- Headless browser / Playwright.
- Persisting offers or source URLs as ListingCase columns.
- Changing `/done`, OLX example collection, or WhatsApp leads.

## Conversation flow

States: `IDLE` → `AWAITING_IDEA` → **`AWAITING_SOURCING_PICK`** →
`AWAITING_CATEGORY_CONFIRM` → `COLLECTING_EXAMPLES`.

1. `/new` asks for the idea (unchanged).
2. On idea text: `ListingBot` sends «Ищу закупки…» **before** `handle`
   returns (same pattern as `/done` + `GENERATING`). The bot knows this is
   an idea because the session is `AWAITING_IDEA` and the text is not a
   slash-command. Then `handle` runs sourcing (this call still blocks).
3. `SourcingScout.search(idea)` returns up to 3 offers sorted by KZT
   ascending.
4. Bot lists them numbered 1–3: title, site name, price in ₸, URL.
5. `1`/`2`/`3`: set session idea to original idea plus chosen title and
   URL; call `classifyCategory`; go to category confirm.
6. `пропустить` (ignore case): classify on original idea; category confirm.
7. Other text: re-prompt to pick 1–3 or пропустить. `/cancel` still resets.

Zero offers: Russian explanation; stay in `AWAITING_SOURCING_PICK`;
`пропустить` still works; user may `/cancel` and `/new`.

## Components

**`SourcingOffer`** — `siteId`, `siteName`, `title`, `url`, `priceKzt`
(integer tenge, rounded).

**`MarketplaceSource`** — `id`, `displayName`, `search(String query) →
List<SourcingOffer>` in that site’s currency; scout converts to KZT.

Each source: search-URL template, GET with browser User-Agent, timeouts
aligned with OLX (5s connect / 10s read), 3 retries on transport errors,
parse a **frozen HTML fixture** in tests. Parse failure or non-200 → empty
list, log, do not throw to the user.

v1 URL templates (query URL-encoded):

- AliExpress: `https://www.aliexpress.com/w/wholesale-{query}.html`
- Alibaba: `https://www.alibaba.com/trade/search?SearchText={query}`
- 1688: `https://s.1688.com/selloffer/offer_search.htm?keywords={query}`
- Amazon.de: `https://www.amazon.de/s?k={query}`
- eBay.de: `https://www.ebay.de/sch/i.html?_nkw={query}`

Take a small number of offers per site (e.g. first 5 parsed 1-pc prices),
then globally sort by KZT and keep 3.

**`SourcingScout`** — loop allowlist, convert prices with config FX,
sort, `limit 3`. Search query is the idea string as typed (no extra LLM
keyword extraction in v1).

**FX** (`application.properties`): `app.fx.usd-kzt`, `app.fx.eur-kzt`,
`app.fx.cny-kzt`. Missing/zero rate → skip that offer. Defaults are
approximate and meant to be edited locally; not live market data.

**`ConversationSession`** — `List<SourcingOffer> sourcingPicks` (max 3).

**`ConversationHandler`** — after idea, do **not** classify until pick or
skip. `persistDraft` stays after category confirm (unchanged).

Russian copy:

- Searching: «Ищу закупки на AliExpress, Alibaba, 1688, Amazon.de, eBay.de…»
- Results: numbered list, price as «N ₸», then «Ответьте 1, 2 или 3 — или
  «пропустить».»
- None: «Не нашёл предложений с ценой за 1 шт. Напишите «пропустить» или
  /cancel.»
- Bad pick: «Ответьте 1, 2, 3 или «пропустить».»

## Errors

- Per-site HTTP/parse failure: skip site, log warn.
- All sites fail or no parseable 1-pc price: zero-offer copy.
- Handler/scout must not crash the polling thread; wrap like other LLM
  steps and use `BotReplies.LLM_ERROR` only for unexpected runtime errors,
  not for empty sourcing.

## Testing

- Each source: `MockRestServiceServer` + HTML fixture → at least one
  offer with a 1-pc price.
- Scout: two sites, mixed currencies → cheapest three in KZT; broken site
  omitted.
- Handler: idea → sourcing state; `2` enriches idea and asks category;
  `пропустить` classifies original idea; garbage re-prompts; `/cancel`
  works.
- No live AliExpress/Amazon/1688/eBay calls in `./mvnw test`.

## Config

```
app.fx.usd-kzt=500
app.fx.eur-kzt=540
app.fx.cny-kzt=70
```

Allowlist order in config or a `List` of source beans. Adding a site later
is a new `MarketplaceSource` implementation plus a fixture test.
