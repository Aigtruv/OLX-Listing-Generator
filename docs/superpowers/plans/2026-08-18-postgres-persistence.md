# PostgreSQL Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the app against PostgreSQL 16 via Docker Compose, while `./mvnw test` keeps using in-memory H2 and never needs Docker.

**Architecture:** Main `application.properties` points at `jdbc:postgresql://localhost:5432/listings`. `src/test/resources/application.properties` overrides to H2 mem so every Spring test stays on H2. Hibernate `ddl-auto=update` creates tables. H2 is `test` scope so it is absent from `spring-boot:run`.

**Tech Stack:** Java 25, Spring Boot 4.1 (Maven, `./mvnw`), Spring Data JPA, PostgreSQL JDBC, H2 (tests only), Docker Compose `postgres:16`.

**Spec:** `docs/superpowers/specs/2026-08-18-postgres-persistence-design.md`

## Global Constraints

- Per repo CLAUDE.md: **always use `List` instead of raw arrays**; convert any library-returned array immediately (`List.of(...)`).
- Per repo CLAUDE.md: **always use Apache Commons `StringUtils`** (`org.apache.commons.lang3.StringUtils`) for string checks/manipulation — never `str.isEmpty()`, `str.trim()` etc. directly.
- User-facing bot text in **Russian**; code, comments, commit messages in English.
- Tokens only from env vars with **empty** defaults: `DEEPSEEK_API_KEY`, `TELEGRAM_BOT_TOKEN`, WhatsApp vars. Never commit real keys. If `src/main/resources/application.properties` currently has inline key defaults after `${ENV:`, strip them to `${DEEPSEEK_API_KEY:}` and `${TELEGRAM_BOT_TOKEN:}`.
- JDBC URL/user/password from `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` with Compose defaults `jdbc:postgresql://localhost:5432/listings`, `listings`, `listings`.
- Hibernate `spring.jpa.hibernate.ddl-auto=update`. No Flyway, no Testcontainers, no H2→Postgres dump, no `h2` runtime profile.
- If Postgres is down, the app must fail at datasource init — no H2 fallback.
- Do not change JPA entities (`ListingCase`, `ExampleListing`, `Lead`).
- Run tests with `./mvnw -q test`; single class: `./mvnw -q -Dtest=ClassName test`.
- Do not commit `./data/` (already gitignored). Do not migrate `./data/listings.mv.db`.

## File map

- Create: `docker-compose.yml` — local Postgres 16
- Create: `src/test/resources/application.properties` — H2 overlay for all tests
- Modify: `pom.xml` — `postgresql` runtime; H2 `test` scope
- Modify: `src/main/resources/application.properties` — Postgres JDBC; empty token defaults
- Modify: `src/test/java/com/example/aiagentpublisher/AiAgentPublisherApplicationTests.java` — assert tests use H2
- Modify: `README.md` — compose + run; Data section

---

### Task 1: Pin tests to in-memory H2

**Files:**
- Create: `src/test/resources/application.properties`
- Modify: `src/test/java/com/example/aiagentpublisher/AiAgentPublisherApplicationTests.java`

**Interfaces:**
- Consumes: existing `@SpringBootTest` / `@DataJpaTest` classes that currently inherit main `jdbc:h2:file:./data/listings`.
- Produces: test classpath properties that force `jdbc:h2:mem:...`; `testsUseH2` asserts the DataSource URL contains `h2`.

- [ ] **Step 1: Write the failing assertion**

Replace `src/test/java/com/example/aiagentpublisher/AiAgentPublisherApplicationTests.java` with:

```java
package com.example.aiagentpublisher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiAgentPublisherApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

    @Test
    void testsUseH2() throws Exception {
        assertThat(dataSource.getConnection().getMetaData().getURL()).contains("h2");
    }
}
```

Do not add Postgres-related properties to this class.

- [ ] **Step 2: Run the new test (should PASS on current main H2 file)**

Run: `./mvnw -q -Dtest=AiAgentPublisherApplicationTests test`
Expected: PASS (`testsUseH2` matches file H2 too). This locks the invariant before flipping main config.

- [ ] **Step 3: Add `src/test/resources/application.properties`**

Create the file (this directory does not exist yet):

```properties
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
```

Do not copy Telegram/DeepSeek keys into this file.

- [ ] **Step 4: Re-run tests**

Run: `./mvnw -q -Dtest=AiAgentPublisherApplicationTests test`
Expected: PASS. Then: `./mvnw -q test`
Expected: ALL PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/resources/application.properties src/test/java/com/example/aiagentpublisher/AiAgentPublisherApplicationTests.java
git commit -m "test: pin Spring tests to in-memory H2"
```

---

### Task 2: Postgres runtime (Compose, driver, main config, README)

**Files:**
- Create: `docker-compose.yml`
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1 H2 test overlay (`testsUseH2` must still pass with no Docker).
- Produces: `docker compose up -d` listens on `localhost:5432` with db/user/password `listings`; runtime JDBC defaults as in the spec.

- [ ] **Step 1: Add `docker-compose.yml` at the repo root**

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: listings
      POSTGRES_USER: listings
      POSTGRES_PASSWORD: listings
    ports:
      - "5432:5432"
    volumes:
      - listings-pg:/var/lib/postgresql/data

volumes:
  listings-pg:
```

- [ ] **Step 2: Switch `pom.xml` dependencies**

Replace the existing H2 block:

```xml
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
```

with:

```xml
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
```

Do not pin a PostgreSQL driver version; the Boot 4.1.0 BOM manages it.

- [ ] **Step 3: Replace datasource + token lines in `src/main/resources/application.properties`**

The file must be exactly:

```properties
spring.application.name=ai-agent-publisher

spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/listings}
spring.datasource.username=${DATABASE_USERNAME:listings}
spring.datasource.password=${DATABASE_PASSWORD:listings}
spring.jpa.hibernate.ddl-auto=update

app.deepseek.api-key=${DEEPSEEK_API_KEY:}
app.deepseek.model=deepseek-chat
app.deepseek.base-url=https://api.deepseek.com
app.session.ttl=PT24H
app.telegram.token=${TELEGRAM_BOT_TOKEN:}

app.whatsapp.token=${WHATSAPP_TOKEN:}
app.whatsapp.phone-number-id=${WHATSAPP_PHONE_NUMBER_ID:}
app.whatsapp.verify-token=${WHATSAPP_VERIFY_TOKEN:}
app.whatsapp.app-secret=${WHATSAPP_APP_SECRET:}
app.whatsapp.graph-base-url=https://graph.facebook.com/v21.0
```

Do not set `spring.datasource.driver-class-name`. Do not put real API keys in this file.

- [ ] **Step 4: Run the full suite WITHOUT Docker (must still be H2)**

Run: `./mvnw -q test`
Expected: ALL PASS. `testsUseH2` PASS. If this fails with a PostgreSQL connection error, the test overlay from Task 1 is missing or not on the test classpath — stop and fix that; do not start Postgres to make tests green.

- [ ] **Step 5: Update `README.md`**

Replace the **Run** section (from `## Run` through the paragraph about env vars) with this exact text:

    ## Run

    Start Postgres, then the app:

        docker compose up -d
        export TELEGRAM_BOT_TOKEN=123456:ABC-your-token
        export DEEPSEEK_API_KEY=sk-your-deepseek-key
        ./mvnw spring-boot:run

    Without the Telegram/DeepSeek env vars the app still starts if Postgres is up
    (bot and DeepSeek calls are disabled). If Postgres is down, the app fails to
    start. Tests do not need Docker.

Use fenced markdown in README (`bash` fence around the four commands) so it matches the rest of the file.

In the WhatsApp env example, keep the same `export` lines but add `docker compose up -d` before `./mvnw spring-boot:run`.

Replace the **Data** section with:

```markdown
## Data

PostgreSQL via `docker-compose.yml` (database `listings`). Hibernate creates
tables on startup. `docker compose down -v` wipes the volume. The old H2 file
`./data/listings.mv.db` is unused.
```

- [ ] **Step 6: Commit**

```bash
git add docker-compose.yml pom.xml src/main/resources/application.properties README.md
git commit -m "feat: persist listings and leads in PostgreSQL"
```

- [ ] **Step 7: Local smoke (human/agent with Docker; not CI)**

If a previous `./mvnw spring-boot:run` is still bound to port 8080, stop it first.

Run: `docker compose up -d`
Expected: container healthy/listening on 5432.

Then start the app the same way as today (Telegram + DeepSeek env vars; on this Mac also `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStoreType=KeychainStore` because of Halykmarket TLS inspection).

Expected log: `Started AiAgentPublisherApplication` and `Telegram bot started (long polling)` with no H2 file path in the datasource URL.

---

## Spec coverage

| Spec item | Task |
|-----------|------|
| Compose Postgres 16, db/user/password `listings`, port 5432, volume | Task 2 |
| `postgresql` runtime, H2 `test` scope | Task 2 |
| Main JDBC env defaults, `ddl-auto=update`, no `driver-class-name` | Task 2 |
| Empty token defaults | Task 2 |
| Test H2 overlay so `./mvnw test` needs no Docker | Task 1 |
| No Flyway / Testcontainers / H2 dump / runtime H2 fallback | omitted by design |
| README run + data | Task 2 |
| Restart running bot | Task 2 Step 7 |
