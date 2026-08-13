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

## WhatsApp qualification bot

Buyers message one shared WhatsApp Cloud API number. The bot shows currently
PUBLISHED listings, asks city / budget / when they want to buy, stores a lead,
and pings you in Telegram. It never sends your number to the buyer.

1. Create a Meta app with WhatsApp → add a test (or production) number.
2. Set the webhook URL to `https://<public-host>/webhooks/whatsapp`
   (use ngrok or Cloudflare Tunnel when running locally) and the verify token
   to the same value as `WHATSAPP_VERIFY_TOKEN`.
3. Subscribe to `messages`.

```bash
export TELEGRAM_BOT_TOKEN=...
export ANTHROPIC_API_KEY=...
export WHATSAPP_TOKEN=...
export WHATSAPP_PHONE_NUMBER_ID=...
export WHATSAPP_VERIFY_TOKEN=...
# optional:
export WHATSAPP_APP_SECRET=...
./mvnw spring-boot:run
```

Put the WhatsApp number on your OLX listing. After `/published` in Telegram,
buyers can message that number.

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
