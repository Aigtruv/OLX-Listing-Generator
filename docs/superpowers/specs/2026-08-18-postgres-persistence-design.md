# PostgreSQL Persistence — Design

Date: 2026-08-18
Status: Approved by user (this session)
Project: ai-agent-publisher (Spring Boot 4.1, Java 25, Maven)

## Purpose

Run the listing bot and WhatsApp leads against PostgreSQL instead of the
H2 file at `./data/listings`. Tests stay on in-memory H2 so `./mvnw test`
does not need Docker.

## Goals

- Runtime database is PostgreSQL 16 via Docker Compose in this repo.
- Same JPA entities and repositories (`ListingCase`, `ExampleListing`,
  `Lead`). No schema redesign.
- Tables created/updated by Hibernate `ddl-auto=update`.
- JDBC URL/user/password from env vars, with Compose defaults for local run.
- H2 is test-scoped only. If Postgres is down, the app fails to start (no
  silent H2 fallback).
- Start with an empty Postgres database. Do not copy the existing H2 file.
- Remove hardcoded `DEEPSEEK_API_KEY` / `TELEGRAM_BOT_TOKEN` defaults from
  `application.properties` if present; keys remain env-only.

## Non-goals

- Flyway / Liquibase.
- Testcontainers or running tests against Postgres.
- A `h2` Spring profile for running the app without Docker.
- Migrating rows from `./data/listings.mv.db`.
- Cloud/managed Postgres, connection pooling tuning, or replica setup.
- Changing listing or lead fields.

## Approach

### Compose

`docker-compose.yml` at the repo root:

- Image `postgres:16`
- Database, user, and password: `listings`
- Host port `5432`
- Named volume for data
- No secrets in git beyond this local default password

### Application

`pom.xml`: `org.postgresql:postgresql` runtime; `com.h2database:h2` `test`
scope.

`src/main/resources/application.properties`:

```
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/listings}
spring.datasource.username=${DATABASE_USERNAME:listings}
spring.datasource.password=${DATABASE_PASSWORD:listings}
spring.jpa.hibernate.ddl-auto=update
```

Do not set `driver-class-name`; Boot selects the PostgreSQL driver. Keep
existing `app.*` properties. Telegram and DeepSeek keys: `${ENV:}` with
empty default.

Entities stay as-is (`@Lob` strings become PostgreSQL `text` under the
current Hibernate dialect). UUID ids are stored as `uuid`.

### Tests

`src/test/resources/application.properties` sets in-memory H2 so every
`@SpringBootTest` / `@DataJpaTest` uses H2 unless a test overrides the URL.
Existing mem-URL overrides on integration tests remain valid.

### Run

```
docker compose up -d
export TELEGRAM_BOT_TOKEN=...
export DEEPSEEK_API_KEY=...
./mvnw spring-boot:run
```

The currently running bot process must be restarted after the swap.

## Testing

`./mvnw test` with no Docker. Assert `contextLoads` and existing JPA /
integration tests still pass on H2. No live Postgres in CI.

## Errors

Postgres unreachable: Spring Boot fails at datasource init with a JDBC
connection error. Do not catch and fall back to H2.
