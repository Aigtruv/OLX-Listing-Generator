# OLX Listing Generator (MVP) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Telegram bot that turns a product idea plus 3–5 pasted OLX.kz example listings into an analysis and an original ready-to-publish listing, persisted with a funnel status.

**Architecture:** Single Spring Boot app. `ConversationHandler` (per-chat state machine, no Telegram types) drives `ListingPipeline` (three Claude structured-output calls: classify → analyze → generate, plus an n-gram `SimilarityGuard`), persists `ListingCase` via Spring Data JPA over file-based H2. A thin `ListingBot` adapter connects Telegram long polling to the handler.

**Tech Stack:** Java 25, Spring Boot 4.1 (Maven, `./mvnw`), Spring Data JPA + H2 (file mode), Anthropic Java SDK `com.anthropic:anthropic-java:2.34.0` (structured outputs), Telegram `org.telegram:telegrambots-longpolling:9.0.0` + `telegrambots-client:9.0.0`, Apache `commons-lang3`, JUnit 5 + Mockito + AssertJ (via `spring-boot-starter-test`).

**Spec:** `docs/superpowers/specs/2026-08-10-olx-listing-generator-design.md`

## Global Constraints

- Per repo CLAUDE.md: **always use `List` instead of raw arrays**; convert any library-returned array immediately (`List.of(...)`).
- Per repo CLAUDE.md: **always use Apache Commons `StringUtils`** (`org.apache.commons.lang3.StringUtils`) for string checks/manipulation — never `str.isEmpty()`, `str.trim()` etc. directly.
- All user-facing bot text in **Russian**; code, comments, commit messages in English.
- Claude model default: **`claude-opus-5`**, configurable via `app.anthropic.model`. API key only from `ANTHROPIC_API_KEY` env var; Telegram token only from `TELEGRAM_BOT_TOKEN` env var. The app must start (and all tests must pass) with **neither env var set**.
- Funnel statuses: `CREATED`, `PUBLISHED`, `HOT`, `COLD` (HOT/COLD unused in MVP but must exist in the enum).
- No OLX scraping, no auto-publishing, no URL fetching.
- Base package: `com.example.aiagentpublisher`.
- Run tests with `./mvnw -q test`; single class: `./mvnw -q -Dtest=ClassName test`.

---

### Task 1: Build setup — dependencies and configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: compile/test classpath with JPA, H2, commons-lang3, anthropic-java 2.34.0, telegrambots 9.0.0; properties `app.anthropic.model`, `app.session.ttl`, `app.telegram.token`.

- [ ] **Step 1: Add dependencies to `pom.xml`**

Insert inside the existing `<dependencies>` block (keep the two existing entries):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>3.17.0</version>
        </dependency>

        <dependency>
            <groupId>com.anthropic</groupId>
            <artifactId>anthropic-java</artifactId>
            <version>2.34.0</version>
        </dependency>

        <dependency>
            <groupId>org.telegram</groupId>
            <artifactId>telegrambots-longpolling</artifactId>
            <version>9.0.0</version>
        </dependency>

        <dependency>
            <groupId>org.telegram</groupId>
            <artifactId>telegrambots-client</artifactId>
            <version>9.0.0</version>
        </dependency>
```

If Maven cannot resolve a pinned version, check the latest on Maven Central for that artifact and use it — do not switch artifacts.

- [ ] **Step 2: Replace `src/main/resources/application.properties`**

```properties
spring.application.name=ai-agent-publisher

spring.datasource.url=jdbc:h2:file:./data/listings
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update

app.anthropic.model=claude-opus-5
app.session.ttl=PT24H
app.telegram.token=${TELEGRAM_BOT_TOKEN:}
```

- [ ] **Step 3: Append the H2 data directory to `.gitignore`**

Add at the end of `.gitignore`:

```
### H2 database ###
data/
```

- [ ] **Step 4: Run the build to verify the context still loads**

Run: `./mvnw -q test`
Expected: `AiAgentPublisherApplicationTests` PASSES (JPA auto-configures against file H2; no env vars needed).

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.properties .gitignore
git commit -m "build: add JPA/H2, commons-lang3, anthropic-java, telegrambots dependencies"
```

---

### Task 2: Domain model and repository

**Files:**
- Create: `src/main/java/com/example/aiagentpublisher/domain/ListingStatus.java`
- Create: `src/main/java/com/example/aiagentpublisher/domain/ExampleListing.java`
- Create: `src/main/java/com/example/aiagentpublisher/domain/ListingCase.java`
- Create: `src/main/java/com/example/aiagentpublisher/domain/ListingCaseRepository.java`
- Test: `src/test/java/com/example/aiagentpublisher/domain/ListingCaseRepositoryTest.java`

**Interfaces:**
- Consumes: Task 1 classpath.
- Produces: entity `ListingCase` (getters/setters for `id: UUID`, `chatId: long`, `ideaText`, `category`, `analysisSummary`, `generatedTitle`, `generatedDescription`, `priceAdvice`: String, `status: ListingStatus`, `createdAt/updatedAt: Instant`, `examples: List<ExampleListing>`); entity `ExampleListing` (`rawText`, `analysis`: String); enum `ListingStatus {CREATED, PUBLISHED, HOT, COLD}`; repository `ListingCaseRepository extends JpaRepository<ListingCase, UUID>` with `List<ListingCase> findByChatIdOrderByCreatedAtDesc(long chatId)` and `Optional<ListingCase> findFirstByChatIdAndStatusOrderByCreatedAtDesc(long chatId, ListingStatus status)`.

- [ ] **Step 1: Write the failing repository test**

`src/test/java/com/example/aiagentpublisher/domain/ListingCaseRepositoryTest.java`:

```java
package com.example.aiagentpublisher.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ListingCaseRepositoryTest {

    @Autowired
    private ListingCaseRepository repository;

    private ListingCase newCase(long chatId, ListingStatus status, Instant createdAt, String title) {
        ListingCase listingCase = new ListingCase();
        listingCase.setChatId(chatId);
        listingCase.setIdeaText("продаю ноутбуки");
        listingCase.setCategory("Электроника → Ноутбуки");
        listingCase.setStatus(status);
        listingCase.setCreatedAt(createdAt);
        listingCase.setGeneratedTitle(title);
        return listingCase;
    }

    @Test
    void savesCaseWithExamplesAndReadsThemBackInOrder() {
        ListingCase listingCase = newCase(1L, ListingStatus.CREATED, null, "t");
        ExampleListing first = new ExampleListing();
        first.setRawText("пример 1");
        ExampleListing second = new ExampleListing();
        second.setRawText("пример 2");
        listingCase.getExamples().add(first);
        listingCase.getExamples().add(second);

        UUID id = repository.saveAndFlush(listingCase).getId();
        ListingCase loaded = repository.findById(id).orElseThrow();

        assertThat(loaded.getExamples()).extracting(ExampleListing::getRawText)
                .containsExactly("пример 1", "пример 2");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(ListingStatus.CREATED);
    }

    @Test
    void findsLatestCreatedCaseForChat() {
        Instant base = Instant.parse("2026-08-10T10:00:00Z");
        repository.save(newCase(7L, ListingStatus.CREATED, base, "older"));
        repository.save(newCase(7L, ListingStatus.CREATED, base.plusSeconds(60), "newer"));
        repository.save(newCase(7L, ListingStatus.PUBLISHED, base.plusSeconds(120), "published"));
        repository.save(newCase(8L, ListingStatus.CREATED, base.plusSeconds(180), "other chat"));
        repository.flush();

        ListingCase latest = repository
                .findFirstByChatIdAndStatusOrderByCreatedAtDesc(7L, ListingStatus.CREATED)
                .orElseThrow();

        assertThat(latest.getGeneratedTitle()).isEqualTo("newer");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=ListingCaseRepositoryTest test`
Expected: COMPILATION ERROR — `ListingCase`, `ListingStatus`, `ExampleListing`, `ListingCaseRepository` do not exist.

- [ ] **Step 3: Implement the domain classes**

`src/main/java/com/example/aiagentpublisher/domain/ListingStatus.java`:

```java
package com.example.aiagentpublisher.domain;

public enum ListingStatus {
    CREATED,
    PUBLISHED,
    HOT,
    COLD
}
```

`src/main/java/com/example/aiagentpublisher/domain/ExampleListing.java`:

```java
package com.example.aiagentpublisher.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.util.UUID;

@Entity
public class ExampleListing {

    @Id
    @GeneratedValue
    private UUID id;

    @Lob
    private String rawText;

    @Lob
    private String analysis;

    public UUID getId() {
        return id;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }
}
```

`src/main/java/com/example/aiagentpublisher/domain/ListingCase.java`:

```java
package com.example.aiagentpublisher.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class ListingCase {

    @Id
    @GeneratedValue
    private UUID id;

    private long chatId;

    @Column(length = 4000)
    private String ideaText;

    private String category;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "listing_case_id")
    @OrderColumn(name = "position")
    private List<ExampleListing> examples = new ArrayList<>();

    @Lob
    private String analysisSummary;

    @Column(length = 1000)
    private String generatedTitle;

    @Lob
    private String generatedDescription;

    @Column(length = 2000)
    private String priceAdvice;

    @Enumerated(EnumType.STRING)
    private ListingStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public String getIdeaText() {
        return ideaText;
    }

    public void setIdeaText(String ideaText) {
        this.ideaText = ideaText;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<ExampleListing> getExamples() {
        return examples;
    }

    public String getAnalysisSummary() {
        return analysisSummary;
    }

    public void setAnalysisSummary(String analysisSummary) {
        this.analysisSummary = analysisSummary;
    }

    public String getGeneratedTitle() {
        return generatedTitle;
    }

    public void setGeneratedTitle(String generatedTitle) {
        this.generatedTitle = generatedTitle;
    }

    public String getGeneratedDescription() {
        return generatedDescription;
    }

    public void setGeneratedDescription(String generatedDescription) {
        this.generatedDescription = generatedDescription;
    }

    public String getPriceAdvice() {
        return priceAdvice;
    }

    public void setPriceAdvice(String priceAdvice) {
        this.priceAdvice = priceAdvice;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
```

`src/main/java/com/example/aiagentpublisher/domain/ListingCaseRepository.java`:

```java
package com.example.aiagentpublisher.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingCaseRepository extends JpaRepository<ListingCase, UUID> {

    List<ListingCase> findByChatIdOrderByCreatedAtDesc(long chatId);

    Optional<ListingCase> findFirstByChatIdAndStatusOrderByCreatedAtDesc(long chatId, ListingStatus status);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -Dtest=ListingCaseRepositoryTest test`
Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/domain src/test/java/com/example/aiagentpublisher/domain
git commit -m "feat: add ListingCase domain model with funnel status and repository"
```

---

### Task 3: LLM contracts — records, gateway interface, prompt factory, Anthropic implementation

**Files:**
- Create: `src/main/java/com/example/aiagentpublisher/llm/CategorySuggestion.java`
- Create: `src/main/java/com/example/aiagentpublisher/llm/ListingAnalysis.java`
- Create: `src/main/java/com/example/aiagentpublisher/llm/GeneratedListing.java`
- Create: `src/main/java/com/example/aiagentpublisher/llm/LlmGateway.java`
- Create: `src/main/java/com/example/aiagentpublisher/llm/PromptFactory.java`
- Create: `src/main/java/com/example/aiagentpublisher/llm/AnthropicGateway.java`
- Test: `src/test/java/com/example/aiagentpublisher/llm/PromptFactoryTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `record CategorySuggestion(String categoryPath)`; `record ListingAnalysis(List<String> perExampleAnalysis, String winningTemplate)`; `record GeneratedListing(String title, String description, String priceAdvice, List<String> photoChecklist)`; `interface LlmGateway { <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType); }`; `PromptFactory` with methods `classifySystem()`, `classifyUser(String idea)`, `analyzeSystem()`, `analyzeUser(String category, List<String> examples)`, `generateSystem()`, `generateUser(String idea, String category, ListingAnalysis analysis, List<String> examples, boolean diverge)` — all returning `String`.

- [ ] **Step 1: Write the failing PromptFactory test**

`src/test/java/com/example/aiagentpublisher/llm/PromptFactoryTest.java`:

```java
package com.example.aiagentpublisher.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptFactoryTest {

    private final PromptFactory prompts = new PromptFactory();

    @Test
    void classifyPromptContainsIdeaAndDemandsRussianCategoryPath() {
        assertThat(prompts.classifySystem()).containsIgnoringCase("olx.kz");
        assertThat(prompts.classifyUser("продаю ноутбуки")).contains("продаю ноутбуки");
    }

    @Test
    void analyzePromptNumbersExamplesAndCarriesCategory() {
        String user = prompts.analyzeUser("Электроника → Ноутбуки", List.of("текст один", "текст два"));
        assertThat(user).contains("Электроника → Ноутбуки");
        assertThat(user).contains("1.").contains("2.");
        assertThat(user).contains("текст один").contains("текст два");
    }

    @Test
    void generatePromptCarriesIdeaTemplateAndExamples() {
        ListingAnalysis analysis = new ListingAnalysis(List.of("а1"), "шаблон успеха");
        String user = prompts.generateUser("продаю ноутбуки", "Электроника → Ноутбуки",
                analysis, List.of("пример"), false);
        assertThat(user).contains("продаю ноутбуки").contains("шаблон успеха").contains("пример");
        assertThat(user).doesNotContain("TOO SIMILAR");
    }

    @Test
    void divergeFlagAddsExplicitDivergeInstruction() {
        ListingAnalysis analysis = new ListingAnalysis(List.of("а1"), "шаблон");
        String user = prompts.generateUser("идея", "категория", analysis, List.of("пример"), true);
        assertThat(user).contains("TOO SIMILAR");
    }

    @Test
    void allSystemPromptsDemandRussianOutput() {
        assertThat(prompts.classifySystem()).containsIgnoringCase("russian");
        assertThat(prompts.analyzeSystem()).containsIgnoringCase("russian");
        assertThat(prompts.generateSystem()).containsIgnoringCase("russian");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=PromptFactoryTest test`
Expected: COMPILATION ERROR — `PromptFactory`, `ListingAnalysis` do not exist.

- [ ] **Step 3: Implement records, interface, and PromptFactory**

`src/main/java/com/example/aiagentpublisher/llm/CategorySuggestion.java`:

```java
package com.example.aiagentpublisher.llm;

public record CategorySuggestion(String categoryPath) {
}
```

`src/main/java/com/example/aiagentpublisher/llm/ListingAnalysis.java`:

```java
package com.example.aiagentpublisher.llm;

import java.util.List;

public record ListingAnalysis(List<String> perExampleAnalysis, String winningTemplate) {
}
```

`src/main/java/com/example/aiagentpublisher/llm/GeneratedListing.java`:

```java
package com.example.aiagentpublisher.llm;

import java.util.List;

public record GeneratedListing(String title, String description, String priceAdvice, List<String> photoChecklist) {
}
```

`src/main/java/com/example/aiagentpublisher/llm/LlmGateway.java`:

```java
package com.example.aiagentpublisher.llm;

public interface LlmGateway {

    <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType);
}
```

`src/main/java/com/example/aiagentpublisher/llm/PromptFactory.java`:

```java
package com.example.aiagentpublisher.llm;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptFactory {

    public String classifySystem() {
        return """
                You are an expert on the OLX.kz classifieds marketplace and its category tree.
                The user gives you a product idea in Russian. Pick the single most fitting
                OLX.kz category path. Answer with the category path in Russian, using the
                format "Раздел → Подраздел", e.g. "Электроника → Ноутбуки и компьютеры".
                """;
    }

    public String classifyUser(String idea) {
        return "Product idea: «%s». Return the best OLX.kz category path.".formatted(idea);
    }

    public String analyzeSystem() {
        return """
                You are a marketplace listing analyst for OLX.kz. You explain why specific
                listings perform well: title hooks, description structure, price positioning,
                photo mentions, trust signals (warranty, receipts, seller tone).
                All output text must be in Russian.
                """;
    }

    public String analyzeUser(String category, List<String> examples) {
        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(category).append("\n\n");
        sb.append("Example listings from this category, one per item:\n\n");
        for (int i = 0; i < examples.size(); i++) {
            sb.append(i + 1).append(". ").append(examples.get(i)).append("\n\n");
        }
        sb.append("""
                For each example, in the same order, write a short analysis (in Russian) of why
                it works or fails: title hook, description structure, price positioning, photos,
                trust signals. Then write a summary "winning template" (in Russian) describing
                what a top listing in this category looks like.
                perExampleAnalysis must contain exactly one entry per example, same order.
                """);
        return sb.toString();
    }

    public String generateSystem() {
        return """
                You write original OLX.kz listings in Russian. You never copy sentences or
                distinctive phrases from example listings — the result must not look like any
                of them. You only state facts the user provided; for unknown specifics use
                placeholders in square brackets, e.g. [укажите модель]. The title must be at
                most 70 characters. All output text must be in Russian.
                """;
    }

    public String generateUser(String idea, String category, ListingAnalysis analysis,
                               List<String> examples, boolean diverge) {
        StringBuilder sb = new StringBuilder();
        sb.append("Product idea: ").append(idea).append("\n");
        sb.append("Category: ").append(category).append("\n\n");
        sb.append("Winning template for this category:\n").append(analysis.winningTemplate()).append("\n\n");
        sb.append("Example listings you must NOT resemble:\n\n");
        for (int i = 0; i < examples.size(); i++) {
            sb.append(i + 1).append(". ").append(examples.get(i)).append("\n\n");
        }
        sb.append("""
                Write one original listing: title (max 70 chars), description, recommended
                price range with one-line reasoning (priceAdvice), and a photo checklist
                (photoChecklist) of 3-6 concrete shots the seller should take.
                """);
        if (diverge) {
            sb.append("""

                    Your previous attempt was TOO SIMILAR to the examples. Rewrite from scratch
                    with different wording, structure and openings. Do not reuse any 8-word
                    sequence from the examples.
                    """);
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -Dtest=PromptFactoryTest test`
Expected: 5 tests PASS.

- [ ] **Step 5: Implement AnthropicGateway (no test — thin SDK wrapper, covered by integration test mock boundary)**

`src/main/java/com/example/aiagentpublisher/llm/AnthropicGateway.java`:

```java
package com.example.aiagentpublisher.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnthropicGateway implements LlmGateway {

    private final String model;
    private final Object lock = new Object();
    private volatile AnthropicClient client;

    public AnthropicGateway(@Value("${app.anthropic.model}") String model) {
        this.model = model;
    }

    // Lazy so the app can start without ANTHROPIC_API_KEY (fails on first use instead).
    private AnthropicClient client() {
        if (client == null) {
            synchronized (lock) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder()
                            .fromEnv()
                            .maxRetries(3)
                            .build();
                }
            }
        }
        return client;
    }

    @Override
    public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(systemPrompt)
                .outputConfig(responseType)
                .addUserMessage(userPrompt)
                .build();
        return client().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Claude returned no text content"))
                .text();
    }
}
```

If the SDK builder rejects `.fromEnv()` on the builder chain, use `AnthropicOkHttpClient.fromEnv()` and drop `.maxRetries(3)` (SDK default is 2 retries) — do not hand-roll retry loops.

- [ ] **Step 6: Verify everything compiles and all tests pass**

Run: `./mvnw -q test`
Expected: all tests PASS (AnthropicGateway compiles; its client is lazy so no API key is needed).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/llm src/test/java/com/example/aiagentpublisher/llm
git commit -m "feat: add LLM gateway with structured-output records and prompt factory"
```

---

### Task 4: SimilarityGuard

**Files:**
- Create: `src/main/java/com/example/aiagentpublisher/pipeline/SimilarityGuard.java`
- Test: `src/test/java/com/example/aiagentpublisher/pipeline/SimilarityGuardTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `@Component SimilarityGuard` with `boolean isTooSimilar(String generatedText, List<String> exampleTexts)` — true when any 8-word shingle (case-insensitive, whitespace-split) of an example appears in the generated text.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/aiagentpublisher/pipeline/SimilarityGuardTest.java`:

```java
package com.example.aiagentpublisher.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarityGuardTest {

    private final SimilarityGuard guard = new SimilarityGuard();

    private static final String EXAMPLE =
            "Продаю отличный мощный ноутбук в идеальном состоянии с гарантией зарядкой и коробкой";

    @Test
    void detectsVerbatimEightWordOverlap() {
        String generated = "Внимание! отличный мощный ноутбук в идеальном состоянии с гарантией — пишите.";
        assertThat(guard.isTooSimilar(generated, List.of(EXAMPLE))).isTrue();
    }

    @Test
    void overlapCheckIsCaseInsensitive() {
        String generated = "ОТЛИЧНЫЙ МОЩНЫЙ НОУТБУК В ИДЕАЛЬНОМ СОСТОЯНИИ С ГАРАНТИЕЙ продам срочно";
        assertThat(guard.isTooSimilar(generated, List.of(EXAMPLE))).isTrue();
    }

    @Test
    void acceptsTextWithoutLongOverlaps() {
        String generated = "Ноутбук для работы и учёбы, быстрый и лёгкий, отдаю с зарядным устройством.";
        assertThat(guard.isTooSimilar(generated, List.of(EXAMPLE))).isFalse();
    }

    @Test
    void shortTextsNeverMatch() {
        assertThat(guard.isTooSimilar("продам ноутбук", List.of("куплю ноутбук"))).isFalse();
        assertThat(guard.isTooSimilar("", List.of(EXAMPLE))).isFalse();
        assertThat(guard.isTooSimilar(null, List.of(EXAMPLE))).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=SimilarityGuardTest test`
Expected: COMPILATION ERROR — `SimilarityGuard` does not exist.

- [ ] **Step 3: Implement SimilarityGuard**

`src/main/java/com/example/aiagentpublisher/pipeline/SimilarityGuard.java`:

```java
package com.example.aiagentpublisher.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SimilarityGuard {

    private static final int SHINGLE_SIZE = 8;

    public boolean isTooSimilar(String generatedText, List<String> exampleTexts) {
        Set<String> generatedShingles = shingles(generatedText);
        if (generatedShingles.isEmpty()) {
            return false;
        }
        for (String example : exampleTexts) {
            Set<String> overlap = shingles(example);
            overlap.retainAll(generatedShingles);
            if (!overlap.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Set<String> shingles(String text) {
        Set<String> result = new HashSet<>();
        if (StringUtils.isBlank(text)) {
            return result;
        }
        List<String> words = List.of(StringUtils.split(StringUtils.lowerCase(text)));
        for (int i = 0; i + SHINGLE_SIZE <= words.size(); i++) {
            result.add(String.join(" ", words.subList(i, i + SHINGLE_SIZE)));
        }
        return result;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -Dtest=SimilarityGuardTest test`
Expected: 4 tests PASS. Note: punctuation attached to words (e.g. `гарантией —`) means shingles differ; the tests are built so overlapping runs contain no punctuation-broken words.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/pipeline/SimilarityGuard.java src/test/java/com/example/aiagentpublisher/pipeline/SimilarityGuardTest.java
git commit -m "feat: add 8-word shingle similarity guard"
```

---

### Task 5: ListingPipeline

**Files:**
- Create: `src/main/java/com/example/aiagentpublisher/pipeline/PipelineResult.java`
- Create: `src/main/java/com/example/aiagentpublisher/pipeline/ListingPipeline.java`
- Test: `src/test/java/com/example/aiagentpublisher/pipeline/ListingPipelineTest.java`

**Interfaces:**
- Consumes: `LlmGateway`, `PromptFactory`, records from Task 3; `SimilarityGuard` from Task 4; `ListingCaseRepository`, `ListingCase`, `ExampleListing`, `ListingStatus` from Task 2.
- Produces: `record PipelineResult(ListingAnalysis analysis, GeneratedListing listing, boolean similarityWarning)`; `@Service ListingPipeline` with `String classifyCategory(String ideaText)` and `PipelineResult run(long chatId, String ideaText, String category, List<String> exampleTexts)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/aiagentpublisher/pipeline/ListingPipelineTest.java`:

```java
package com.example.aiagentpublisher.pipeline;

import com.example.aiagentpublisher.domain.ExampleListing;
import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.CategorySuggestion;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;
import com.example.aiagentpublisher.llm.LlmGateway;
import com.example.aiagentpublisher.llm.PromptFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingPipelineTest {

    private static final String EXAMPLE =
            "Продаю отличный мощный ноутбук в идеальном состоянии с гарантией зарядкой и коробкой";

    @Mock
    private LlmGateway llm;

    @Mock
    private ListingCaseRepository repository;

    private ListingPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new ListingPipeline(llm, new PromptFactory(), new SimilarityGuard(), repository);
        when(repository.save(any(ListingCase.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void classifyDelegatesToLlm() {
        when(llm.generate(anyString(), contains("продаю ноутбуки"), eq(CategorySuggestion.class)))
                .thenReturn(new CategorySuggestion("Электроника → Ноутбуки"));

        assertThat(pipeline.classifyCategory("продаю ноутбуки")).isEqualTo("Электроника → Ноутбуки");
    }

    @Test
    void happyPathSavesCreatedCaseWithPerExampleAnalysis() {
        when(llm.generate(anyString(), anyString(), eq(ListingAnalysis.class)))
                .thenReturn(new ListingAnalysis(List.of("анализ 1"), "шаблон"));
        when(llm.generate(anyString(), anyString(), eq(GeneratedListing.class)))
                .thenReturn(new GeneratedListing("Ноутбук для учёбы", "Совсем другое описание без совпадений.",
                        "150 000 тг", List.of("фото экрана")));

        PipelineResult result = pipeline.run(9L, "продаю ноутбуки", "Электроника → Ноутбуки", List.of(EXAMPLE));

        assertThat(result.similarityWarning()).isFalse();
        assertThat(result.listing().title()).isEqualTo("Ноутбук для учёбы");

        ArgumentCaptor<ListingCase> captor = ArgumentCaptor.forClass(ListingCase.class);
        verify(repository).save(captor.capture());
        ListingCase saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ListingStatus.CREATED);
        assertThat(saved.getChatId()).isEqualTo(9L);
        assertThat(saved.getGeneratedTitle()).isEqualTo("Ноутбук для учёбы");
        assertThat(saved.getAnalysisSummary()).isEqualTo("шаблон");
        assertThat(saved.getExamples()).hasSize(1);
        assertThat(saved.getExamples().get(0).getRawText()).isEqualTo(EXAMPLE);
        assertThat(saved.getExamples().get(0).getAnalysis()).isEqualTo("анализ 1");
    }

    @Test
    void regeneratesOnceWhenTooSimilarThenSucceeds() {
        when(llm.generate(anyString(), anyString(), eq(ListingAnalysis.class)))
                .thenReturn(new ListingAnalysis(List.of("а"), "шаблон"));
        when(llm.generate(anyString(), anyString(), eq(GeneratedListing.class)))
                .thenReturn(
                        new GeneratedListing("Копия", EXAMPLE, "100", List.of("ф")),
                        new GeneratedListing("Оригинал", "Полностью новое описание своими словами.", "100", List.of("ф")));

        PipelineResult result = pipeline.run(1L, "идея", "категория", List.of(EXAMPLE));

        assertThat(result.similarityWarning()).isFalse();
        assertThat(result.listing().title()).isEqualTo("Оригинал");
        verify(llm, times(2)).generate(anyString(), anyString(), eq(GeneratedListing.class));
    }

    @Test
    void keepsWarningWhenRegenerationStillTooSimilar() {
        when(llm.generate(anyString(), anyString(), eq(ListingAnalysis.class)))
                .thenReturn(new ListingAnalysis(List.of("а"), "шаблон"));
        when(llm.generate(anyString(), anyString(), eq(GeneratedListing.class)))
                .thenReturn(
                        new GeneratedListing("Копия", EXAMPLE, "100", List.of("ф")),
                        new GeneratedListing("Копия 2", EXAMPLE, "100", List.of("ф")));

        PipelineResult result = pipeline.run(1L, "идея", "категория", List.of(EXAMPLE));

        assertThat(result.similarityWarning()).isTrue();
        assertThat(result.listing().title()).isEqualTo("Копия 2");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=ListingPipelineTest test`
Expected: COMPILATION ERROR — `ListingPipeline`, `PipelineResult` do not exist.

- [ ] **Step 3: Implement PipelineResult and ListingPipeline**

`src/main/java/com/example/aiagentpublisher/pipeline/PipelineResult.java`:

```java
package com.example.aiagentpublisher.pipeline;

import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;

public record PipelineResult(ListingAnalysis analysis, GeneratedListing listing, boolean similarityWarning) {
}
```

`src/main/java/com/example/aiagentpublisher/pipeline/ListingPipeline.java`:

```java
package com.example.aiagentpublisher.pipeline;

import com.example.aiagentpublisher.domain.ExampleListing;
import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.CategorySuggestion;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;
import com.example.aiagentpublisher.llm.LlmGateway;
import com.example.aiagentpublisher.llm.PromptFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListingPipeline {

    private final LlmGateway llm;
    private final PromptFactory prompts;
    private final SimilarityGuard similarityGuard;
    private final ListingCaseRepository repository;

    public ListingPipeline(LlmGateway llm, PromptFactory prompts,
                           SimilarityGuard similarityGuard, ListingCaseRepository repository) {
        this.llm = llm;
        this.prompts = prompts;
        this.similarityGuard = similarityGuard;
        this.repository = repository;
    }

    public String classifyCategory(String ideaText) {
        return llm.generate(prompts.classifySystem(), prompts.classifyUser(ideaText),
                CategorySuggestion.class).categoryPath();
    }

    @Transactional
    public PipelineResult run(long chatId, String ideaText, String category, List<String> exampleTexts) {
        ListingAnalysis analysis = llm.generate(prompts.analyzeSystem(),
                prompts.analyzeUser(category, exampleTexts), ListingAnalysis.class);

        GeneratedListing listing = llm.generate(prompts.generateSystem(),
                prompts.generateUser(ideaText, category, analysis, exampleTexts, false),
                GeneratedListing.class);

        boolean similarityWarning = false;
        if (isTooSimilar(listing, exampleTexts)) {
            listing = llm.generate(prompts.generateSystem(),
                    prompts.generateUser(ideaText, category, analysis, exampleTexts, true),
                    GeneratedListing.class);
            similarityWarning = isTooSimilar(listing, exampleTexts);
        }

        repository.save(toCase(chatId, ideaText, category, exampleTexts, analysis, listing));
        return new PipelineResult(analysis, listing, similarityWarning);
    }

    private boolean isTooSimilar(GeneratedListing listing, List<String> exampleTexts) {
        return similarityGuard.isTooSimilar(listing.title() + " " + listing.description(), exampleTexts);
    }

    private ListingCase toCase(long chatId, String ideaText, String category, List<String> exampleTexts,
                               ListingAnalysis analysis, GeneratedListing listing) {
        ListingCase listingCase = new ListingCase();
        listingCase.setChatId(chatId);
        listingCase.setIdeaText(ideaText);
        listingCase.setCategory(category);
        listingCase.setAnalysisSummary(analysis.winningTemplate());
        listingCase.setGeneratedTitle(listing.title());
        listingCase.setGeneratedDescription(listing.description());
        listingCase.setPriceAdvice(listing.priceAdvice());
        listingCase.setStatus(ListingStatus.CREATED);
        for (int i = 0; i < exampleTexts.size(); i++) {
            ExampleListing example = new ExampleListing();
            example.setRawText(exampleTexts.get(i));
            if (i < analysis.perExampleAnalysis().size()) {
                example.setAnalysis(analysis.perExampleAnalysis().get(i));
            }
            listingCase.getExamples().add(example);
        }
        return listingCase;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -Dtest=ListingPipelineTest test`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/pipeline src/test/java/com/example/aiagentpublisher/pipeline
git commit -m "feat: add listing pipeline with similarity-checked regeneration"
```

---

### Task 6: Conversation session store and handler

**Files:**
- Create: `src/main/java/com/example/aiagentpublisher/config/AppConfig.java`
- Create: `src/main/java/com/example/aiagentpublisher/bot/ConversationState.java`
- Create: `src/main/java/com/example/aiagentpublisher/bot/ConversationSession.java`
- Create: `src/main/java/com/example/aiagentpublisher/bot/ConversationSessionStore.java`
- Create: `src/main/java/com/example/aiagentpublisher/bot/BotReplies.java`
- Create: `src/main/java/com/example/aiagentpublisher/bot/ConversationHandler.java`
- Test: `src/test/java/com/example/aiagentpublisher/bot/ConversationSessionStoreTest.java`
- Test: `src/test/java/com/example/aiagentpublisher/bot/ConversationHandlerTest.java`

**Interfaces:**
- Consumes: `ListingPipeline`, `PipelineResult` (Task 5); `ListingCaseRepository`, `ListingCase`, `ListingStatus` (Task 2); `ListingAnalysis`, `GeneratedListing` (Task 3).
- Produces: `@Service ConversationHandler` with `List<String> handle(long chatId, String rawText)`; `@Component ConversationSessionStore` with `ConversationSession get(long chatId)` and `void reset(long chatId)`; `@Configuration AppConfig` exposing a `java.time.Clock` bean (`Clock.systemUTC()`).

- [ ] **Step 1: Write the failing session store test**

`src/test/java/com/example/aiagentpublisher/bot/ConversationSessionStoreTest.java`:

```java
package com.example.aiagentpublisher.bot;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationSessionStoreTest {

    @Test
    void returnsSameSessionWithinTtl() {
        ConversationSessionStore store =
                new ConversationSessionStore(Clock.systemUTC(), Duration.ofHours(24));

        ConversationSession first = store.get(1L);
        first.setState(ConversationState.AWAITING_IDEA);

        assertThat(store.get(1L).getState()).isEqualTo(ConversationState.AWAITING_IDEA);
    }

    @Test
    void expiresSessionAfterTtl() {
        Instant start = Instant.parse("2026-08-10T10:00:00Z");
        Clock clock = mock(Clock.class);
        // get() reads the clock exactly once per call: first get -> start, second get -> +25h
        when(clock.instant()).thenReturn(start, start.plus(Duration.ofHours(25)));
        ConversationSessionStore store = new ConversationSessionStore(clock, Duration.ofHours(24));

        store.get(1L).setState(ConversationState.COLLECTING_EXAMPLES);

        assertThat(store.get(1L).getState()).isEqualTo(ConversationState.IDLE);
    }

    @Test
    void resetDropsSession() {
        ConversationSessionStore store =
                new ConversationSessionStore(Clock.fixed(Instant.now(), ZoneOffset.UTC), Duration.ofHours(24));
        store.get(1L).setState(ConversationState.AWAITING_IDEA);

        store.reset(1L);

        assertThat(store.get(1L).getState()).isEqualTo(ConversationState.IDLE);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=ConversationSessionStoreTest test`
Expected: COMPILATION ERROR — classes do not exist.

- [ ] **Step 3: Implement state, session, store, and Clock config**

`src/main/java/com/example/aiagentpublisher/config/AppConfig.java`:

```java
package com.example.aiagentpublisher.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

`src/main/java/com/example/aiagentpublisher/bot/ConversationState.java`:

```java
package com.example.aiagentpublisher.bot;

public enum ConversationState {
    IDLE,
    AWAITING_IDEA,
    AWAITING_CATEGORY_CONFIRM,
    COLLECTING_EXAMPLES
}
```

`src/main/java/com/example/aiagentpublisher/bot/ConversationSession.java`:

```java
package com.example.aiagentpublisher.bot;

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

    public Instant getLastActivity() {
        return lastActivity;
    }
}
```

`src/main/java/com/example/aiagentpublisher/bot/ConversationSessionStore.java`:

```java
package com.example.aiagentpublisher.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationSessionStore {

    private final Map<Long, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public ConversationSessionStore(Clock clock, @Value("${app.session.ttl:PT24H}") Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public ConversationSession get(long chatId) {
        Instant now = clock.instant();
        ConversationSession session = sessions.get(chatId);
        if (session == null || Duration.between(session.getLastActivity(), now).compareTo(ttl) > 0) {
            session = new ConversationSession(chatId, now);
            sessions.put(chatId, session);
        }
        session.touch(now);
        return session;
    }

    public void reset(long chatId) {
        sessions.remove(chatId);
    }
}
```

- [ ] **Step 4: Run store test to verify it passes**

Run: `./mvnw -q -Dtest=ConversationSessionStoreTest test`
Expected: 3 tests PASS.

- [ ] **Step 5: Commit the store**

```bash
git add src/main/java/com/example/aiagentpublisher/config src/main/java/com/example/aiagentpublisher/bot src/test/java/com/example/aiagentpublisher/bot
git commit -m "feat: add conversation session store with TTL expiry"
```

- [ ] **Step 6: Write the failing handler test**

`src/test/java/com/example/aiagentpublisher/bot/ConversationHandlerTest.java`:

```java
package com.example.aiagentpublisher.bot;

import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;
import com.example.aiagentpublisher.pipeline.ListingPipeline;
import com.example.aiagentpublisher.pipeline.PipelineResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationHandlerTest {

    @Mock
    private ListingPipeline pipeline;

    @Mock
    private ListingCaseRepository repository;

    private ConversationHandler handler;

    private static PipelineResult okResult() {
        return new PipelineResult(
                new ListingAnalysis(List.of("анализ 1", "анализ 2", "анализ 3"), "шаблон успеха"),
                new GeneratedListing("Ноутбук Dell", "Описание без совпадений.", "150 000 тг",
                        List.of("фото экрана", "фото клавиатуры")),
                false);
    }

    @BeforeEach
    void setUp() {
        ConversationSessionStore store =
                new ConversationSessionStore(Clock.systemUTC(), Duration.ofHours(24));
        handler = new ConversationHandler(store, pipeline, repository);
    }

    private void driveToExamples(long chatId) {
        when(pipeline.classifyCategory("продаю ноутбуки")).thenReturn("Электроника → Ноутбуки");
        handler.handle(chatId, "/new");
        handler.handle(chatId, "продаю ноутбуки");
        handler.handle(chatId, "да");
    }

    @Test
    void fullHappyFlowProducesListingMessages() {
        driveToExamples(1L);
        when(pipeline.run(eq(1L), eq("продаю ноутбуки"), eq("Электроника → Ноутбуки"),
                eq(List.of("пример 1", "пример 2", "пример 3")))).thenReturn(okResult());

        handler.handle(1L, "пример 1");
        handler.handle(1L, "пример 2");
        handler.handle(1L, "пример 3");
        List<String> replies = handler.handle(1L, "/done");

        String all = String.join("\n", replies);
        assertThat(all).contains("Ноутбук Dell").contains("шаблон успеха")
                .contains("150 000 тг").contains("фото экрана");
        assertThat(all).doesNotContain(BotReplies.SIMILARITY_WARNING);
    }

    @Test
    void categoryCorrectionUsesUserText() {
        when(pipeline.classifyCategory("идея")).thenReturn("Неверная категория");
        when(pipeline.run(anyLong(), anyString(), eq("Моя категория"), anyList())).thenReturn(okResult());

        handler.handle(2L, "/new");
        handler.handle(2L, "идея");
        handler.handle(2L, "Моя категория");
        handler.handle(2L, "пример");
        handler.handle(2L, "/done");

        verify(pipeline).run(eq(2L), eq("идея"), eq("Моя категория"), eq(List.of("пример")));
    }

    @Test
    void fewerThanThreeExamplesWarnsButProceeds() {
        driveToExamples(3L);
        when(pipeline.run(anyLong(), anyString(), anyString(), anyList())).thenReturn(okResult());

        handler.handle(3L, "пример 1");
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
            handler.handle(5L, "пример " + i);
        }

        List<String> replies = handler.handle(5L, "лишний пример");

        assertThat(replies).containsExactly(BotReplies.EXAMPLES_LIMIT);
    }

    @Test
    void pipelineFailureKeepsStateAndExamples() {
        driveToExamples(6L);
        handler.handle(6L, "пример 1");
        when(pipeline.run(anyLong(), anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("api down"))
                .thenReturn(okResult());

        List<String> failed = handler.handle(6L, "/done");
        List<String> retried = handler.handle(6L, "/done");

        assertThat(failed).contains(BotReplies.LLM_ERROR);
        assertThat(String.join("\n", retried)).contains("Ноутбук Dell");
    }

    @Test
    void classifyFailureKeepsAwaitingIdea() {
        when(pipeline.classifyCategory("идея"))
                .thenThrow(new RuntimeException("api down"))
                .thenReturn("Категория");

        handler.handle(7L, "/new");
        List<String> failed = handler.handle(7L, "идея");
        List<String> retried = handler.handle(7L, "идея");

        assertThat(failed).containsExactly(BotReplies.LLM_ERROR);
        assertThat(retried.get(0)).contains("Категория");
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
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `./mvnw -q -Dtest=ConversationHandlerTest test`
Expected: COMPILATION ERROR — `ConversationHandler`, `BotReplies` do not exist.

- [ ] **Step 8: Implement BotReplies and ConversationHandler**

`src/main/java/com/example/aiagentpublisher/bot/BotReplies.java`:

```java
package com.example.aiagentpublisher.bot;

public final class BotReplies {

    public static final String ASK_IDEA =
            "Опишите вашу идею — что продаём? Например: «продаю ноутбуки».";
    public static final String CATEGORY_CONFIRM =
            "Категория: %s%nВерно? Ответьте «да» или пришлите свой вариант категории.";
    public static final String ASK_EXAMPLES =
            "Теперь пришлите 3–5 текстов успешных объявлений из этой категории "
                    + "(каждое отдельным сообщением). Когда закончите — /done.";
    public static final String EXAMPLE_ACCEPTED = "Пример %d принят. Ещё один или /done.";
    public static final String EXAMPLES_LIMIT = "Максимум 5 примеров. Отправьте /done.";
    public static final String NEED_EXAMPLES =
            "Нужен хотя бы один пример объявления. Пришлите текст или /cancel.";
    public static final String FEW_EXAMPLES_WARNING =
            "Примеров меньше трёх — анализ будет менее точным, но продолжаю.";
    public static final String LLM_ERROR =
            "Не получилось получить ответ от ИИ. Попробуйте ещё раз чуть позже — ваши данные сохранены.";
    public static final String CANCELLED = "Ок, отменил. /new — начать заново.";
    public static final String PUBLISHED_OK = "Отметил как опубликованное: «%s». Удачных продаж!";
    public static final String NOTHING_TO_PUBLISH =
            "Нет свежих сгенерированных объявлений. Сначала /new.";
    public static final String NO_CASES = "Пока нет ни одного объявления. Начните с /new.";
    public static final String HINT =
            "Команды: /new — новое объявление, /status — мои объявления, "
                    + "/published — отметить опубликованным, /cancel — отменить.";
    public static final String SIMILARITY_WARNING =
            "⚠️ Текст всё ещё похож на примеры — перед публикацией перефразируйте вручную.";

    private BotReplies() {
    }
}
```

`src/main/java/com/example/aiagentpublisher/bot/ConversationHandler.java`:

```java
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
```

- [ ] **Step 9: Run test to verify it passes**

Run: `./mvnw -q -Dtest=ConversationHandlerTest test`
Expected: 11 tests PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/bot src/test/java/com/example/aiagentpublisher/bot
git commit -m "feat: add conversation state machine handler with Russian replies"
```

---

### Task 7: Telegram adapter

**Files:**
- Create: `src/main/java/com/example/aiagentpublisher/bot/ListingBot.java`
- Create: `src/main/java/com/example/aiagentpublisher/bot/TelegramBotStarter.java`
- Test: `src/test/java/com/example/aiagentpublisher/bot/ListingBotTest.java`

**Interfaces:**
- Consumes: `ConversationHandler.handle(long, String)` (Task 6).
- Produces: `ListingBot implements LongPollingSingleThreadUpdateConsumer` (constructor `ListingBot(ConversationHandler, TelegramClient)`); `@Component TelegramBotStarter` that registers the bot on startup only when `app.telegram.token` is non-blank.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/aiagentpublisher/bot/ListingBotTest.java`:

```java
package com.example.aiagentpublisher.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingBotTest {

    @Mock
    private ConversationHandler handler;

    @Mock
    private TelegramClient telegramClient;

    private Update textUpdate(long chatId, String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getText()).thenReturn(text);
        return update;
    }

    @Test
    void forwardsTextAndSendsEachReply() throws TelegramApiException {
        when(handler.handle(42L, "/new")).thenReturn(List.of("ответ 1", "ответ 2"));
        ListingBot bot = new ListingBot(handler, telegramClient);

        bot.consume(textUpdate(42L, "/new"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, org.mockito.Mockito.times(2)).execute(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SendMessage::getChatId, SendMessage::getText)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("42", "ответ 1"),
                        org.assertj.core.groups.Tuple.tuple("42", "ответ 2"));
    }

    @Test
    void ignoresUpdatesWithoutTextMessage() {
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(false);
        ListingBot bot = new ListingBot(handler, telegramClient);

        bot.consume(update);

        verifyNoInteractions(handler, telegramClient);
    }
}
```

If `org.telegram.telegrambots.meta.api.objects.message.Message` does not resolve, the library version uses `org.telegram.telegrambots.meta.api.objects.Message` — fix the import, nothing else.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=ListingBotTest test`
Expected: COMPILATION ERROR — `ListingBot` does not exist.

- [ ] **Step 3: Implement ListingBot and TelegramBotStarter**

`src/main/java/com/example/aiagentpublisher/bot/ListingBot.java`:

```java
package com.example.aiagentpublisher.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

public class ListingBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ListingBot.class);

    private final ConversationHandler handler;
    private final TelegramClient telegramClient;

    public ListingBot(ConversationHandler handler, TelegramClient telegramClient) {
        this.handler = handler;
        this.telegramClient = telegramClient;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        long chatId = update.getMessage().getChatId();
        List<String> replies = handler.handle(chatId, update.getMessage().getText());
        for (String reply : replies) {
            try {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(reply)
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to send reply to chat {}", chatId, e);
            }
        }
    }
}
```

`src/main/java/com/example/aiagentpublisher/bot/TelegramBotStarter.java`:

```java
package com.example.aiagentpublisher.bot;

import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

@Component
public class TelegramBotStarter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotStarter.class);

    private final ConversationHandler handler;
    private final String token;
    private TelegramBotsLongPollingApplication botsApplication;

    public TelegramBotStarter(ConversationHandler handler,
                              @Value("${app.telegram.token:}") String token) {
        this.handler = handler;
        this.token = token;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (StringUtils.isBlank(token)) {
            log.warn("TELEGRAM_BOT_TOKEN is not set — Telegram bot not started");
            return;
        }
        botsApplication = new TelegramBotsLongPollingApplication();
        botsApplication.registerBot(token, new ListingBot(handler, new OkHttpTelegramClient(token)));
        log.info("Telegram bot started (long polling)");
    }

    @PreDestroy
    public void shutdown() throws Exception {
        if (botsApplication != null) {
            botsApplication.close();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -Dtest=ListingBotTest test`
Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aiagentpublisher/bot src/test/java/com/example/aiagentpublisher/bot
git commit -m "feat: add Telegram long-polling adapter with conditional startup"
```

---

### Task 8: Integration test and run instructions

**Files:**
- Test: `src/test/java/com/example/aiagentpublisher/ListingFlowIntegrationTest.java`
- Create: `README.md`

**Interfaces:**
- Consumes: everything above; mocks only `LlmGateway`.
- Produces: green end-to-end proof; setup docs.

- [ ] **Step 1: Write the failing integration test**

`src/test/java/com/example/aiagentpublisher/ListingFlowIntegrationTest.java`:

```java
package com.example.aiagentpublisher;

import com.example.aiagentpublisher.bot.ConversationHandler;
import com.example.aiagentpublisher.domain.ListingCase;
import com.example.aiagentpublisher.domain.ListingCaseRepository;
import com.example.aiagentpublisher.domain.ListingStatus;
import com.example.aiagentpublisher.llm.CategorySuggestion;
import com.example.aiagentpublisher.llm.GeneratedListing;
import com.example.aiagentpublisher.llm.ListingAnalysis;
import com.example.aiagentpublisher.llm.LlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1"
})
class ListingFlowIntegrationTest {

    @Autowired
    private ConversationHandler handler;

    @Autowired
    private ListingCaseRepository repository;

    @MockitoBean
    private LlmGateway llmGateway;

    @Test
    void fullFlowPersistsCaseAndPublishesIt() {
        when(llmGateway.generate(anyString(), anyString(), eq(CategorySuggestion.class)))
                .thenReturn(new CategorySuggestion("Электроника → Ноутбуки"));
        when(llmGateway.generate(anyString(), anyString(), eq(ListingAnalysis.class)))
                .thenReturn(new ListingAnalysis(List.of("анализ 1", "анализ 2", "анализ 3"), "шаблон"));
        when(llmGateway.generate(anyString(), anyString(), eq(GeneratedListing.class)))
                .thenReturn(new GeneratedListing("Ноутбук Dell XPS", "Оригинальное описание.",
                        "180 000 тг", List.of("фото экрана")));

        long chatId = 100L;
        handler.handle(chatId, "/new");
        handler.handle(chatId, "продаю ноутбуки");
        handler.handle(chatId, "да");
        handler.handle(chatId, "пример 1");
        handler.handle(chatId, "пример 2");
        handler.handle(chatId, "пример 3");
        List<String> replies = handler.handle(chatId, "/done");

        assertThat(String.join("\n", replies)).contains("Ноутбук Dell XPS");

        List<ListingCase> cases = repository.findByChatIdOrderByCreatedAtDesc(chatId);
        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getStatus()).isEqualTo(ListingStatus.CREATED);
        assertThat(cases.get(0).getExamples()).hasSize(3);

        handler.handle(chatId, "/published");

        assertThat(repository.findByChatIdOrderByCreatedAtDesc(chatId).get(0).getStatus())
                .isEqualTo(ListingStatus.PUBLISHED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails or passes for the right reason**

Run: `./mvnw -q -Dtest=ListingFlowIntegrationTest test`
Expected: PASS (all pieces already exist). If it fails, the failure is a real wiring bug — fix the wiring, not the test.

- [ ] **Step 3: Write README.md**

````markdown
# ai-agent-publisher

Telegram bot that turns a product idea plus 3–5 example OLX.kz listings into an
analysis of why those listings win and an original, ready-to-publish listing in
Russian. Listings are stored with funnel statuses (CREATED / PUBLISHED / HOT / COLD)
for future dashboards.

## Setup

1. Create a bot: open Telegram, talk to `@BotFather`, send `/newbot`, follow the
   prompts, copy the token.
2. Get an Anthropic API key: https://platform.claude.com/

## Run

```bash
export TELEGRAM_BOT_TOKEN=123456:ABC-your-token
export ANTHROPIC_API_KEY=sk-ant-your-key
./mvnw spring-boot:run
```

Without the env vars the app still starts (bot and Claude calls are disabled) —
useful for tests.

## Use

In your bot's chat:

1. `/new` → describe the idea ("продаю ноутбуки")
2. Confirm the suggested OLX category (or type your own)
3. Paste 3–5 example listings from that category, then `/done`
4. Copy the generated title/description to OLX, then send `/published`

Other commands: `/status` — your listings and statuses, `/cancel` — abort.

## Data

H2 database file at `./data/listings.mv.db`. Delete it to start fresh.

## Tests

```bash
./mvnw test
```
````

- [ ] **Step 4: Run the full suite**

Run: `./mvnw -q test`
Expected: ALL tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/aiagentpublisher/ListingFlowIntegrationTest.java README.md
git commit -m "test: add end-to-end flow integration test and README"
```

---

## Manual smoke test (after all tasks, requires real tokens)

1. `export TELEGRAM_BOT_TOKEN=...` and `export ANTHROPIC_API_KEY=...`
2. `./mvnw spring-boot:run`
3. In Telegram: `/new` → «продаю ноутбуки» → «да» → paste 3 real OLX listings → `/done`
4. Verify: analysis + listing arrive in Russian, `/status` shows CREATED, `/published` flips it.
