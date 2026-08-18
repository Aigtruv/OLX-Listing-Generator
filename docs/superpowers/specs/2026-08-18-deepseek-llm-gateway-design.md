# DeepSeek LLM Gateway — Design

Date: 2026-08-18
Status: Approved by user (this session)
Project: ai-agent-publisher

## Purpose

Replace Anthropic/Claude as the listing-generator LLM with DeepSeek’s public
Chat Completions API so the app can run without an Anthropic API key.

## Goals

- Same `LlmGateway.generate(system, user, Class<T>)` contract used by
  `ListingPipeline`.
- Official DeepSeek API only: `POST https://api.deepseek.com/chat/completions`.
- Key from `DEEPSEEK_API_KEY` env var. App starts (and tests pass) when unset;
  first generate fails with a clear error.
- JSON object responses parsed into existing records:
  `CategorySuggestion`, `ListingAnalysis`, `GeneratedListing`.
- Remove the `anthropic-java` dependency and `ANTHROPIC_API_KEY`.

## Non-goals

- Keeping Anthropic as a configurable provider.
- OpenAI SDK, OpenRouter, Ollama, or keyless public demos.
- Changing Telegram, WhatsApp, prompts’ Russian copy, or pipeline stages.

## Approach

`DeepSeekGateway` implements `LlmGateway`. It sends system + user messages,
sets `response_format.type = json_object`, appends a short JSON-shape hint
for `T`, then Jackson-maps `choices[0].message.content` to `T`. Strip optional
markdown fences. Three HTTP retries on `RestClientException`. Timeouts 5s
connect / 30s read (listing calls are slower than WhatsApp).

Default model: `deepseek-chat` (`app.deepseek.model`).

## Testing

Unit tests with `MockRestServiceServer`: parse category JSON; blank key does
not call HTTP. Existing pipeline/integration tests keep mocking `LlmGateway`.
No live DeepSeek calls in CI.
