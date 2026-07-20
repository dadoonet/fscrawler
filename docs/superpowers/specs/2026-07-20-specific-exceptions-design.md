# Specific FSCrawler exceptions (A then B)

## Goal

Replace generic `RuntimeException` wraps in production code with domain-specific unchecked exceptions, then introduce a shared unchecked base type so callers can catch the whole family.

## Phase A (specific types, no base hierarchy yet)

Add in `framework`:

- `FsCrawlerSerializationException` — Jackson/JSON/XML (de)serialization failures
- `FsCrawlerIOException` — framework filesystem / classpath resource I/O
- `FsCrawlerMappingException` — Elasticsearch mapping mismatches
- `FsCrawlerSourceNotFoundException` — crawl root / URL does not exist

Reuse existing types where they already fit:

- `FsCrawlerIllegalConfigurationException` — invalid settings, missing Tika config
- `FsCrawlerPluginException` — plugin close failures
- `ElasticsearchClientException` — SSL context setup failures

Also preserve exception causes in `DocUtils` and `FsCrawlerExtensionFsProviderAbstract`.

Out of scope for A: Awaitility control-flow wraps in `ElasticsearchClient`, test-only `RuntimeException`s, narrowing public `throws Exception` APIs.

## Phase B (unchecked base)

Add `FsCrawlerException extends RuntimeException` in `framework`.

Make these extend it:

- `FsCrawlerIllegalConfigurationException`
- `FsCrawlerSerializationException`
- `FsCrawlerIOException`
- `FsCrawlerMappingException`
- `FsCrawlerSourceNotFoundException`
- `FsCrawlerPluginException` (plugin module; depends on framework)
- `NetworkErrorRecoveryException` (core)

Keep `ElasticsearchClientException` as a **checked** exception (extends `Exception`) — it stays outside the unchecked hierarchy.

## Rationale

Unchecked exceptions match FSCrawler’s current style for config/plugin/crawl failures. A shared base enables `catch (FsCrawlerException e)` in CLI/REST without forcing checked `throws` cascades.
