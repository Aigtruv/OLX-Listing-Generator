# OLX Listing URL Examples Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After category confirm, the Telegram bot collects 3–5 olx.kz listing URLs, fetches title/price/description, and feeds that text into the existing DeepSeek pipeline; paste text is allowed only after a failed fetch.

**Architecture:** `OlxListingFetcher` (RestClient GET + HTML extract) is separate from `ConversationHandler`. Handler validates URL, fetches, stores formatted text in `ConversationSession.examples`, and sets `awaitingPasteFallback` on fetch failure. `ListingPipeline` is unchanged.

**Tech Stack:** Java 25, Spring Boot 4.1, RestClient, MockRestServiceServer, Apache Commons Lang `StringUtils`, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-19-olx-url-examples-design.md`

## Global Constraints

- Per repo CLAUDE.md: **always use `List` instead of raw arrays**; convert any library-returned array immediately (`List.of(...)`).
- Per repo CLAUDE.md: **always use Apache Commons `StringUtils`** for string checks/manipulation — never `str.isEmpty()`, `str.trim()` etc. directly.
- User-facing bot text in **Russian**; code, comments, commit messages in English.
- No Playwright. No live olx.kz HTTP in tests. No new DB columns. Do not change `ListingPipeline` / DeepSeek contracts.
- Tokens stay env-only. No secrets in git.
- Run tests: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q test`

## File map

- Create: `src/main/java/com/example/aiagentpublisher/olx/OlxListing.java`
- Create: `src/main/java/com/example/aiagentpublisher/olx/OlxListingFetcher.java`
- Create: `src/test/java/com/example/aiagentpublisher/olx/OlxListingFetcherTest.java`
- Modify: `src/main/java/com/example/aiagentpublisher/config/AppConfig.java`
- Modify: `src/main/java/com/example/aiagentpublisher/bot/ConversationSession.java`
- Modify: `src/main/java/com/example/aiagentpublisher/bot/ConversationHandler.java`
- Modify: `src/main/java/com/example/aiagentpublisher/bot/BotReplies.java`
- Modify: `src/test/java/com/example/aiagentpublisher/bot/ConversationHandlerTest.java`
- Modify: `README.md` (example step mentions links)

---

### Task 1: OlxListingFetcher

**Files:**
- Create: `src/main/java/com/example/aiagentpublisher/olx/OlxListing.java`
- Create: `src/main/java/com/example/aiagentpublisher/olx/OlxListingFetcher.java`
- Create: `src/test/java/com/example/aiagentpublisher/olx/OlxListingFetcherTest.java`
- Modify: `src/main/java/com/example/aiagentpublisher/config/AppConfig.java`

**Interfaces:**
- Consumes: Spring `RestClient` (new `@Bean("olxRestClient")`, no base URL).
- Produces: `OlxListingFetcher.isListingUrl(String)`, `Optional<OlxListing> fetch(String url)`, `OlxListing.formatForPipeline()`.

- [ ] **Step 1: Write failing tests**

Create `OlxListingFetcherTest` with `MockRestServiceServer` bound to `RestClient.builder()` (same pattern as `DeepSeekGatewayTest`).

```java
package com.example.aiagentpublisher.olx;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpStatus;

class OlxListingFetcherTest {

    private static final String URL =
            "https://www.olx.kz/d/obyavlenie/noutbuk-dell-inspiron-3162-IDqzxSX.html";

    private static final String HTML = """
            <html><head>
            <meta property="og:title" content="Ноутбук dell inspiron 3162">
            <meta property="og:description" content="Ноутбук рабочий, подходит для учёбы.">
            </head><body>
            <h1>Ноутбук dell inspiron 3162</h1>
            <p>8 000 тг.</p>
            </body></html>
            """;

    @Test
    void acceptsOlxListingUrls() {
        OlxListingFetcher fetcher = new OlxListingFetcher(RestClient.create());
        assertThat(fetcher.isListingUrl(URL)).isTrue();
        assertThat(fetcher.isListingUrl("https://olx.kz/d/obyavlenie/foo.html?foo=1")).isTrue();
        assertThat(fetcher.isListingUrl("https://example.com/d/obyavlenie/foo.html")).isFalse();
        assertThat(fetcher.isListingUrl("просто текст")).isFalse();
        assertThat(fetcher.isListingUrl("  ")).isFalse();
    }

    @Test
    void extractsTitlePriceDescription() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(HTML, MediaType.TEXT_HTML));

        Optional<OlxListing> listing = new OlxListingFetcher(builder.build()).fetch(URL);

        assertThat(listing).isPresent();
        assertThat(listing.get().title()).isEqualTo("Ноутбук dell inspiron 3162");
        assertThat(listing.get().description()).contains("учёбы");
        assertThat(listing.get().formatForPipeline()).contains(URL).contains("8 000 тг");
        server.verify();
    }

    @Test
    void http404ReturnsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(new OlxListingFetcher(builder.build()).fetch(URL)).isEmpty();
        server.verify();
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL** (classes missing)

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q -Dtest=OlxListingFetcherTest test`

- [ ] **Step 3: Implement**

`OlxListing.java`:

```java
package com.example.aiagentpublisher.olx;

public record OlxListing(String url, String title, String price, String description) {

    public String formatForPipeline() {
        return url + "\n" + title + "\n" + price + "\n" + description;
    }
}
```

`OlxListingFetcher.java`:

- Constructor: `RestClient restClient`.
- `isListingUrl`: `StringUtils.trim`, parse with `URI`; host equals `olx.kz` or `www.olx.kz` (ignore case); path starts with `/d/obyavlenie/`; scheme http or https. On parse failure return false. Use `StringUtils` for all string checks.
- `fetch`: if `!isListingUrl` return `Optional.empty()`. GET the URL with header `User-Agent: Mozilla/5.0`. 3 attempts on `RestClientException`. Non-2xx or blank body → empty. Parse:
  - `og:title` via `property="og:title"` `content="..."`
  - `og:description` similarly
  - price: first match of `[0-9][0-9 \\u00a0]*тг` in the HTML (after stripping tags for search), else empty string
  - If title is blank after parse → `Optional.empty()`
  - HTML-unescape `&quot;` `&amp;` `&nbsp;` with `StringUtils.replace`
- Strip tags for description fallback: if og:description blank, take text after stripping `<>` from body, collapse whitespace with `StringUtils.normalizeSpace`, cap at 2000 chars.

Add `@Bean("olxRestClient")` in `AppConfig` like WhatsApp (5s connect / 10s read) **without** `.baseUrl(...)`.

`OlxListingFetcher` is a `@Service` taking `@Qualifier("olxRestClient") RestClient`.

- [ ] **Step 4: Run tests — expect PASS**

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q -Dtest=OlxListingFetcherTest test`

Then full: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q test`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/olx src/test/java/com/example/aiagentpublisher/olx src/main/java/com/example/aiagentpublisher/config/AppConfig.java
git commit -m "feat: fetch and parse olx.kz listing pages"
```

---

### Task 2: ConversationHandler URL examples + paste fallback

**Files:**
- Modify: `BotReplies.java`, `ConversationSession.java`, `ConversationHandler.java`, `ConversationHandlerTest.java`, `README.md`

**Interfaces:**
- Consumes: `OlxListingFetcher` from Task 1.
- Produces: example collection uses URLs; `awaitingPasteFallback` on the session.

- [ ] **Step 1: Update `BotReplies`**

Replace `ASK_EXAMPLES` and `NEED_EXAMPLES`; add `ASK_OLX_URL` and `OLX_FETCH_FAILED`:

```java
    public static final String ASK_EXAMPLES =
            "Теперь пришлите 3–5 ссылок на объявления olx.kz (каждое отдельным сообщением), например:\n"
                    + "https://www.olx.kz/d/obyavlenie/noutbuk-dell-inspiron-3162-IDqzxSX.html\n"
                    + "Когда закончите — /done.";
    public static final String ASK_OLX_URL =
            "Пришлите ссылку на объявление olx.kz, например:\n"
                    + "https://www.olx.kz/d/obyavlenie/…";
    public static final String OLX_FETCH_FAILED =
            "Не удалось открыть объявление. Пришлите другую ссылку или вставьте текст объявления.";
    public static final String NEED_EXAMPLES =
            "Нужен хотя бы один пример объявления. Пришлите ссылку olx.kz или /cancel.";
```

Keep `EXAMPLE_ACCEPTED` as-is.

- [ ] **Step 2: Add `awaitingPasteFallback` on `ConversationSession`**

`private boolean awaitingPasteFallback;` with getter/setter. `setState` does not have to clear it; `/new` and `/cancel` already `sessions.reset` (new session object). After a successful example add, handler must `session.setAwaitingPasteFallback(false)`.

- [ ] **Step 3: Rewrite `collectExample` and inject fetcher**

Constructor: add `OlxListingFetcher olxListingFetcher`.

```java
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
        return List.of(BotReplies.EXAMPLE_ACCEPTED.formatted(session.getExamples().size()));
    }
```

- [ ] **Step 4: Fix `ConversationHandlerTest` (will fail until handler uses fetcher)**

Add `@Mock OlxListingFetcher olxListingFetcher`.

In `@BeforeEach`: `handler = new ConversationHandler(store, pipeline, repository, olxListingFetcher);`

Default stub for tests that still send `"пример …"` (paste path): they must first fail a fetch **or** we change them to URLs.

**Required behavior for existing flows:** update every test that collects examples:

- Happy path / few examples / sixth / pipeline failure / category correction / cancel: after `driveToExamples`, either:
  - `when(olxListingFetcher.isListingUrl(anyString())).thenReturn(true);`
    `when(olxListingFetcher.fetch(anyString())).thenAnswer(inv -> Optional.of(new OlxListing(inv.getArgument(0), "t", "1 тг", inv.getArgument(0))));`
    and pass URLs like `https://www.olx.kz/d/obyavlenie/p1.html`, **or**
  - keep `"пример N"` by stubbing `isListingUrl` false and `awaitingPasteFallback` via a failed fetch first.

Prefer URL + successful fetch stubs so tests match the main path. Adjust `pipeline.run(..., eq(List.of(...)))` to expect `formatForPipeline()` strings.

Add new tests:

```java
    @Test
    void nonOlxTextWhileCollectingAsksForUrl() {
        driveToExamples(20L);
        when(olxListingFetcher.isListingUrl("просто текст")).thenReturn(false);

        assertThat(handler.handle(20L, "просто текст")).containsExactly(BotReplies.ASK_OLX_URL);
    }

    @Test
    void fetchFailureThenPasteIsAccepted() {
        driveToExamples(21L);
        String url = "https://www.olx.kz/d/obyavlenie/x.html";
        when(olxListingFetcher.isListingUrl(url)).thenReturn(true);
        when(olxListingFetcher.fetch(url)).thenReturn(Optional.empty());
        when(olxListingFetcher.isListingUrl("вставленный текст")).thenReturn(false);
        when(pipeline.run(anyLong(), anyString(), anyString(), eq(List.of("вставленный текст"))))
                .thenReturn(okResult());

        assertThat(handler.handle(21L, url)).containsExactly(BotReplies.OLX_FETCH_FAILED);
        handler.handle(21L, "вставленный текст");
        List<String> replies = handler.handle(21L, "/done");
        assertThat(String.join("\n", replies)).contains("Ноутбук Dell");
    }
```

Use `lenient()` on unused stubs if Mockito complains.

- [ ] **Step 5: Run tests**

Run: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q -Dtest=ConversationHandlerTest,OlxListingFetcherTest test`

Then: `env -u TELEGRAM_BOT_TOKEN -u DEEPSEEK_API_KEY ./mvnw -q test`

Expected: ALL PASS.

- [ ] **Step 6: README**

In the **Use** section, change step 3 from pasting example listing texts to sending olx.kz listing links (one per message), then `/done`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/bot src/test/java/com/example/aiagentpublisher/bot/ConversationHandlerTest.java README.md
git commit -m "feat: collect OLX listing URLs as competitor examples"
```

---

## Spec coverage

| Spec item | Task |
|-----------|------|
| isListingUrl www/non-www + query | Task 1 |
| GET + extract title/price/description | Task 1 |
| Invalid URL re-prompt | Task 2 |
| Fetch fail → paste fallback | Task 2 |
| Store formatted text in existing examples list | Task 2 |
| Pipeline unchanged | (no task) |
| MockRestServiceServer, no live OLX | Task 1 |
