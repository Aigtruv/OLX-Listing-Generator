# Wholesale Sourcing in /new Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After the seller types an idea in `/new`, search a fixed China/Europe marketplace allowlist for 1-piece buy prices, show the three cheapest in ₸, let them pick 1/2/3 (or skip), then continue the existing category + OLX listing flow.

**Architecture:** `SourcingScout` asks each `MarketplaceSource` for raw offers (HTTP GET search HTML + parse). `FxRates` converts to KZT. The scout sorts and keeps three. `ConversationHandler` inserts `AWAITING_SOURCING_PICK` between idea and category. `ListingBot` sends a searching ack while the session is `AWAITING_IDEA` (same idea as `/done` + `GENERATING`).

**Tech Stack:** Java 25, Spring Boot 4.1, `RestClient`, JUnit 5, Mockito, `MockRestServiceServer`, Apache Commons `StringUtils`. No new Maven dependencies.

## Global Constraints

- Use `List` never raw arrays; convert library arrays with `List.of(...)`.
- String checks/manipulation: `org.apache.commons.lang3.StringUtils` only.
- User-facing bot text in Russian; code, comments, commits in English.
- No live AliExpress / Alibaba / 1688 / Amazon / eBay HTTP in `./mvnw test`.
- Display and sort prices in ₸ only. Rank by 1-piece KZT, cheapest first.
- Allowlist: AliExpress, Alibaba, 1688.com, Amazon.de, eBay.de.
- Do not invent prices. Per-site fetch/parse failure → skip that site.
- No new DB columns/tables. Chosen offer is appended to session idea text.
- Run tests with `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q test` so the Telegram starter does not long-poll.
- Do not put API tokens in `application.properties`; keep `${ENV:}` empty defaults for secrets. FX rates in properties are OK.

## File map

| File | Responsibility |
| --- | --- |
| `src/main/java/com/example/aiagentpublisher/sourcing/RawSourcingOffer.java` | Site offer before FX |
| `src/main/java/com/example/aiagentpublisher/sourcing/SourcingOffer.java` | Offer in KZT |
| `src/main/java/com/example/aiagentpublisher/sourcing/FxRates.java` | USD/EUR/CNY → KZT |
| `src/main/java/com/example/aiagentpublisher/sourcing/MarketplaceSource.java` | One site search |
| `src/main/java/com/example/aiagentpublisher/sourcing/SourcingScout.java` | Fan-out, convert, top 3 |
| `src/main/java/com/example/aiagentpublisher/sourcing/HtmlSearchSource.java` | Shared GET + `article.offer` parse |
| `src/main/java/com/example/aiagentpublisher/config/AppConfig.java` | `sourcingRestClient` + five source beans |
| `ConversationState`, `ConversationSession`, `ConversationHandler`, `BotReplies`, `ListingBot`, `TelegramBotStarter` | Flow + ack |
| `src/main/resources/application.properties` and test overlay | `app.fx.*` |

---

### Task 1: FxRates + SourcingScout

**Files:**
- Create: `src/main/java/com/example/aiagentpublisher/sourcing/RawSourcingOffer.java`
- Create: `src/main/java/com/example/aiagentpublisher/sourcing/SourcingOffer.java`
- Create: `src/main/java/com/example/aiagentpublisher/sourcing/MarketplaceSource.java`
- Create: `src/main/java/com/example/aiagentpublisher/sourcing/FxRates.java`
- Create: `src/main/java/com/example/aiagentpublisher/sourcing/SourcingScout.java`
- Test: `src/test/java/com/example/aiagentpublisher/sourcing/SourcingScoutTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks
- Produces: `RawSourcingOffer(String siteId, String siteName, String title, String url, double amount, String currency)`; `SourcingOffer(String siteId, String siteName, String title, String url, long priceKzt)`; `MarketplaceSource` with `String id()`, `String displayName()`, `List<RawSourcingOffer> search(String query)`; `FxRates.toKzt(double amount, String currency)` → `Optional<Long>`; `SourcingScout.search(String query)` → `List<SourcingOffer>` (size ≤ 3, cheapest first)

- [ ] **Step 1: Write the failing test**

```java
package com.example.aiagentpublisher.sourcing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourcingScoutTest {

    @Test
    void sortsByKztAndKeepsThreeCheapestSkippingBrokenSource() {
        MarketplaceSource cheap = query -> List.of(
                new RawSourcingOffer("ebay", "eBay.de", "B", "https://e/b", 10, "EUR"));
        MarketplaceSource cheaper = query -> List.of(
                new RawSourcingOffer("ali", "AliExpress", "A", "https://a/a", 5, "USD"),
                new RawSourcingOffer("ali", "AliExpress", "C", "https://a/c", 20, "USD"));
        MarketplaceSource broken = query -> {
            throw new IllegalStateException("blocked");
        };
        SourcingScout scout = new SourcingScout(
                List.of(cheap, cheaper, broken), new FxRates(500, 540, 70));

        List<SourcingOffer> top = scout.search("gps трекеры");

        assertThat(top).extracting(SourcingOffer::title).containsExactly("A", "B", "C");
        assertThat(top.get(0).priceKzt()).isEqualTo(2500L);
        assertThat(top.get(1).priceKzt()).isEqualTo(5400L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q -Dtest=SourcingScoutTest test`

Expected: COMPILATION ERROR — `SourcingScout` / records do not exist.

- [ ] **Step 3: Write minimal implementation**

`RawSourcingOffer.java` / `SourcingOffer.java` — public records as in Interfaces.

`MarketplaceSource.java`:

```java
package com.example.aiagentpublisher.sourcing;

import java.util.List;

public interface MarketplaceSource {
    default String id() {
        return "";
    }

    default String displayName() {
        return "";
    }

    List<RawSourcingOffer> search(String query);
}
```

(The test uses lambdas, so `search` is the only required method. Later beans override `id`/`displayName`.)

`FxRates.java`: constructor `(double usdKzt, double eurKzt, double cnyKzt)`. `toKzt`: blank currency → empty; `KZT`/`тг` → round `amount`; `USD` → `amount * usdKzt`; `EUR` → `amount * eurKzt`; `CNY`/`RMB` → `amount * cnyKzt`; else empty. Use `StringUtils.equalsIgnoreCase`. If the matching rate is `<= 0`, empty. Round with `Math.round`.

`SourcingScout.java`: `@Service` optional in this task (plain class is enough; Task 2/3 will `@Service` it). Constructor `(List<MarketplaceSource> sources, FxRates fx)`. `search`: for each source, try `source.search(query)`, catch `RuntimeException`, log warn, continue. For each raw offer skip if `StringUtils` is blank title/url/amount<=0. Convert via fx; skip empty. Collect `SourcingOffer`s, sort by `priceKzt` ascending, return `List.copyOf(subList(0, min(3, size)))`.

- [ ] **Step 4: Run tests**

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q -Dtest=SourcingScoutTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/sourcing src/test/java/com/example/aiagentpublisher/sourcing/SourcingScoutTest.java
git commit -m "$(cat <<'EOF'
feat: rank marketplace offers by KZT for sourcing scout

EOF
)"
```

---

### Task 2: HTML search sources (allowlist)

**Files:**
- Create: `src/main/java/com/example/aiagentpublisher/sourcing/HtmlSearchSource.java`
- Modify: `src/main/java/com/example/aiagentpublisher/config/AppConfig.java`
- Modify: `src/main/resources/application.properties` (add `app.fx.usd-kzt=500`, `app.fx.eur-kzt=540`, `app.fx.cny-kzt=70` only — do not touch token lines)
- Modify: `src/test/resources/application.properties` (same three `app.fx.*` keys)
- Create: `src/main/java/com/example/aiagentpublisher/sourcing/SourcingConfig.java` if you prefer not to grow `AppConfig` — either is fine; this plan uses `AppConfig`.
- Test: `src/test/java/com/example/aiagentpublisher/sourcing/HtmlSearchSourceTest.java`

**Interfaces:**
- Consumes: `RawSourcingOffer`, `MarketplaceSource`
- Produces: `HtmlSearchSource` constructor `(String id, String displayName, String urlTemplate, String defaultCurrency, RestClient restClient)` where `urlTemplate` contains `%s` for the encoded query; `search` GET that URL with `User-Agent: Mozilla/5.0`, 5s/10s timeouts on the client bean, 3 retries on `RestClientException`, HTTP 4xx/5xx → empty list; parse `article.offer` blocks (see below). Five Spring beans. `FxRates` + `SourcingScout` as `@Service`/`@Component` taking `List<MarketplaceSource>` + `@Value` fx properties.

Parse each:

```html
<article class="offer">
  <a href="https://www.ebay.de/itm/1">Mini GPS</a>
  <span class="price">EUR 8.50</span>
</article>
```

- `href` and link text = url + title (`StringUtils.trim`).
- Price: `EUR|USD|CNY` then number with `.` or `,` decimal. Amount parsed as double (`StringUtils.replace` comma → dot). Currency from that code; if missing, `defaultCurrency`.
- Max 5 offers per page. Blank title/url/price → skip that article.
- Live marketplace HTML often will not match `article.offer`; then that site returns empty (spec: skip). Do not scrape logins or invent rows.

URL templates (query = `URLEncoder.encode(query, UTF_8)` then put in `%s`):

- AliExpress `https://www.aliexpress.com/w/wholesale-%s.html` default USD
- Alibaba `https://www.alibaba.com/trade/search?SearchText=%s` default USD
- 1688 `https://s.1688.com/selloffer/offer_search.htm?keywords=%s` default CNY
- Amazon.de `https://www.amazon.de/s?k=%s` default EUR
- eBay.de `https://www.ebay.de/sch/i.html?_nkw=%s` default EUR

- [ ] **Step 1: Write the failing test**

```java
package com.example.aiagentpublisher.sourcing;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HtmlSearchSourceTest {

    @Test
    void parsesArticleOffersFromSearchHtml() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String html = """
                <article class="offer"><a href="https://www.ebay.de/itm/1">Mini GPS</a>
                <span class="price">EUR 8.50</span></article>
                """;
        server.expect(requestTo("http://localhost/sch/i.html?_nkw=gps"))
                .andRespond(withSuccess(html, MediaType.TEXT_HTML));

        HtmlSearchSource source = new HtmlSearchSource(
                "ebay", "eBay.de", "http://localhost/sch/i.html?_nkw=%s", "EUR", builder.build());
        List<RawSourcingOffer> offers = source.search("gps");

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).title()).isEqualTo("Mini GPS");
        assertThat(offers.get(0).amount()).isEqualTo(8.5);
        assertThat(offers.get(0).currency()).isEqualTo("EUR");
        server.verify();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q -Dtest=HtmlSearchSourceTest test`

Expected: COMPILATION ERROR — `HtmlSearchSource` missing.

- [ ] **Step 3: Implement `HtmlSearchSource` + beans + FxRates wiring**

GET body as `byte[]` then `new String(bytes, UTF_8)` (same OLX charset pitfall). Retries like `OlxListingFetcher`. Catch `RestClientResponseException` → empty list + warn. Never throw to caller.

Make `FxRates` a `@Component` with:

```java
public FxRates(@Value("${app.fx.usd-kzt}") double usdKzt,
               @Value("${app.fx.eur-kzt}") double eurKzt,
               @Value("${app.fx.cny-kzt}") double cnyKzt)
```

Make `SourcingScout` a `@Service` constructor `(List<MarketplaceSource> sources, FxRates fx)`.

In `AppConfig`, add `sourcingRestClient` (same timeouts as `olxRestClient`) and five `@Bean MarketplaceSource` methods returning `new HtmlSearchSource(...)` with the templates above and `@Qualifier("sourcingRestClient")`.

- [ ] **Step 4: Run tests**

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q -Dtest=HtmlSearchSourceTest,SourcingScoutTest,AiAgentPublisherApplicationTests test`

Expected: PASS (`contextLoads` must still see `app.fx.*` on the test overlay).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/sourcing/HtmlSearchSource.java src/main/java/com/example/aiagentpublisher/config/AppConfig.java src/main/resources/application.properties src/test/resources/application.properties src/test/java/com/example/aiagentpublisher/sourcing/HtmlSearchSourceTest.java
git commit -m "$(cat <<'EOF'
feat: fetch marketplace search HTML for sourcing allowlist

EOF
)"
```

---

### Task 3: ConversationHandler sourcing step

**Files:**
- Modify: `src/main/java/com/example/aiagentpublisher/bot/ConversationState.java`
- Modify: `src/main/java/com/example/aiagentpublisher/bot/ConversationSession.java`
- Modify: `src/main/java/com/example/aiagentpublisher/bot/BotReplies.java`
- Modify: `src/main/java/com/example/aiagentpublisher/bot/ConversationHandler.java`
- Modify: `src/test/java/com/example/aiagentpublisher/bot/ConversationHandlerTest.java`
- Modify: `src/test/java/com/example/aiagentpublisher/ListingFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `SourcingScout.search(String)`, `SourcingOffer`
- Produces: state `AWAITING_SOURCING_PICK`; session field `List<SourcingOffer> sourcingPicks` with getter; idea after pick = original + `"\nЗакупка: " + title + " " + url`; `BotReplies.SOURCING_SEARCHING`, `SOURCING_NONE`, `SOURCING_PICK`, `SOURCING_BAD_PICK`. Format results in handler: numbered `1. title — N ₸\nurl` (₸ only).

Russian strings (exact):

```java
public static final String SOURCING_SEARCHING =
        "Ищу закупки на AliExpress, Alibaba, 1688, Amazon.de, eBay.de…";
public static final String SOURCING_NONE =
        "Не нашёл предложений с ценой за 1 шт. Напишите «пропустить» или /cancel.";
public static final String SOURCING_PICK =
        "Ответьте 1, 2 или 3 — или «пропустить».";
public static final String SOURCING_BAD_PICK =
        "Ответьте 1, 2, 3 или «пропустить».";
```

`captureIdea`: set `ideaText`, call `scout.search(idea)`, store picks, set `AWAITING_SOURCING_PICK`, return results list **or** `SOURCING_NONE`. Do **not** classify yet.

`pickSourcing`: if `StringUtils.equalsIgnoreCase(text, "пропустить")` → `classifyAndAskCategory(session, session.getIdeaText())`. If text is `1`/`2`/`3` (trim) and index in range → `classifyAndAskCategory` with enriched idea. Else `SOURCING_BAD_PICK`.

`classifyAndAskCategory`: existing classify try/catch (`LLM_ERROR`), then `AWAITING_CATEGORY_CONFIRM` + `CATEGORY_CONFIRM`.

`handleText` switch includes `AWAITING_SOURCING_PICK`.

- [ ] **Step 1: Write failing handler tests**

Add `@Mock SourcingScout sourcingScout`. Constructor: `(store, pipeline, repository, olxListingFetcher, sourcingScout)`.

`lenient().when(sourcingScout.search(anyString())).thenReturn(List.of());`

Update `driveToExamples` to:

```java
handler.handle(chatId, "/new");
handler.handle(chatId, "продаю ноутбуки");
handler.handle(chatId, "пропустить");
handler.handle(chatId, "да");
```

New tests:

```java
@Test
void ideaShowsTopOffersThenPickEnrichesIdea() {
    when(sourcingScout.search("хочу продавать gps трекеры")).thenReturn(List.of(
            new SourcingOffer("ali", "AliExpress", "GPS A", "https://a", 1000),
            new SourcingOffer("ebay", "eBay.de", "GPS B", "https://b", 2000),
            new SourcingOffer("amz", "Amazon.de", "GPS C", "https://c", 3000)));
    when(pipeline.classifyCategory(org.mockito.ArgumentMatchers.contains("GPS B")))
            .thenReturn("Электроника → GPS");

    handler.handle(50L, "/new");
    List<String> found = handler.handle(50L, "хочу продавать gps трекеры");
    assertThat(String.join("\n", found)).contains("GPS A").contains("1000 ₸").contains(BotReplies.SOURCING_PICK);

    List<String> afterPick = handler.handle(50L, "2");
    assertThat(afterPick.get(0)).contains("Электроника → GPS");
    verify(pipeline).classifyCategory(org.mockito.ArgumentMatchers.contains("https://b"));
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
```

Use static imports for `contains` if you prefer `ArgumentMatchers.contains`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q -Dtest=ConversationHandlerTest test`

Expected: FAIL / COMPILE error until constructor and states exist; `driveToExamples` will hit category too early until implemented.

- [ ] **Step 3: Implement handler/session/replies**

Session: `private final List<SourcingOffer> sourcingPicks = new ArrayList<>();` getter returning the list (same pattern as `examples`).

Integration test `ListingFlowIntegrationTest`: after idea, `handler.handle(chatId, "пропустить");` before `"да"`. Mock `SourcingScout` with `@MockitoBean` returning `List.of()` so the test does not call live HTTP.

- [ ] **Step 4: Run tests**

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/bot src/test/java/com/example/aiagentpublisher/bot/ConversationHandlerTest.java src/test/java/com/example/aiagentpublisher/ListingFlowIntegrationTest.java
git commit -m "$(cat <<'EOF'
feat: ask seller to pick a cheap wholesale offer in /new

EOF
)"
```

---

### Task 4: Searching ack in ListingBot + README

**Files:**
- Modify: `src/main/java/com/example/aiagentpublisher/bot/ListingBot.java`
- Modify: `src/main/java/com/example/aiagentpublisher/bot/TelegramBotStarter.java`
- Modify: `src/test/java/com/example/aiagentpublisher/bot/ListingBotTest.java`
- Modify: `README.md` (Use section: after idea, sourcing pick, then category)

**Interfaces:**
- Consumes: `ConversationSessionStore.get(chatId).getState()`, `BotReplies.SOURCING_SEARCHING`
- Produces: `ListingBot(ConversationHandler, TelegramClient, ConversationSessionStore)`; if state is `AWAITING_IDEA` and text is not blank and does not start with `/`, send `SOURCING_SEARCHING` then `handle`. Keep `/done` → `GENERATING`.

- [ ] **Step 1: Write the failing test**

Inject `@Mock ConversationSessionStore sessions`. Build a real store in the test instead:

```java
@Test
void ideaWhileAwaitingIdeaSendsSearchingAckFirst() throws TelegramApiException {
    ConversationSessionStore store =
            new ConversationSessionStore(Clock.systemUTC(), Duration.ofHours(24));
    store.get(42L).setState(ConversationState.AWAITING_IDEA);
    when(handler.handle(42L, "gps трекеры")).thenReturn(List.of("1. …"));
    ListingBot bot = new ListingBot(handler, telegramClient, store);

    bot.consume(textUpdate(42L, "gps трекеры"));

    InOrder order = inOrder(telegramClient, handler);
    ArgumentCaptor<SendMessage> first = ArgumentCaptor.forClass(SendMessage.class);
    order.verify(telegramClient).execute(first.capture());
    assertThat(first.getValue().getText()).isEqualTo(BotReplies.SOURCING_SEARCHING);
    order.verify(handler).handle(42L, "gps трекеры");
}
```

Update existing `new ListingBot(handler, telegramClient)` calls to pass `new ConversationSessionStore(Clock.systemUTC(), Duration.ofHours(24))` (IDLE state → no extra ack).

- [ ] **Step 2: Run test to verify it fails**

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q -Dtest=ListingBotTest test`

Expected: COMPILE fail on 3-arg constructor / assertion fail on first message.

- [ ] **Step 3: Implement**

```java
if (StringUtils.equals(text, "/done")) {
    send(chatId, BotReplies.GENERATING);
} else if (sessions.get(chatId).getState() == ConversationState.AWAITING_IDEA
        && StringUtils.isNotBlank(text)
        && !StringUtils.startsWith(text, "/")) {
    send(chatId, BotReplies.SOURCING_SEARCHING);
}
```

Note: `sessions.get` touches TTL and may create a session. `ConversationHandler.handle` will `get` the same chat id — OK.

`TelegramBotStarter`: inject `ConversationSessionStore`, `new ListingBot(handler, telegramClient, sessions)`.

README Use steps:

1. `/new` → idea
2. Wait for «Ищу закупки…», then pick `1`/`2`/`3` or `пропустить`
3. Confirm category
4. OLX links + `/done`

- [ ] **Step 4: Run full suite**

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/bot/ListingBot.java src/main/java/com/example/aiagentpublisher/bot/TelegramBotStarter.java src/test/java/com/example/aiagentpublisher/bot/ListingBotTest.java README.md
git commit -m "$(cat <<'EOF'
feat: ack marketplace search before /new sourcing blocks

EOF
)"
```

---

## Spec coverage

| Spec item | Task |
| --- | --- |
| Step after idea, before category | 3 |
| Allowlist 5 sites | 2 |
| 1-pc, sort ₸, display ₸ only | 1, 3 |
| HTTP GET + parse, no invented prices | 2 |
| Skip failed sites, 0–3 results | 1, 3 |
| Pick 1/2/3 or пропустить | 3 |
| Enrich idea with title+URL | 3 |
| Searching ack | 4 |
| FX config | 2 |
| Tests without live marketplaces | 1–4 |
| No new DB table | (none) |

## Placeholder scan

None of TBD / “handle edge cases” without code. Live HTML that does not contain `article.offer` yields empty that site — stated in Task 2.
