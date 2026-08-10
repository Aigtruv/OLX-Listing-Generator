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
