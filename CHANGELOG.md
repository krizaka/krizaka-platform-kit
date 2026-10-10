# Changelog

Versions follow [Semantic Versioning](https://semver.org/) per repository; the compatible set of every Krizaka JVM
artifact is the one `krizaka-bom` carries. Releases are written by
[release-please](https://github.com/googleapis/release-please) from the Conventional Commits merged on `main`; the
hand-written history is under *Before release-please*.

## [0.2.0](https://github.com/krizaka/krizaka-platform-kit/compare/v0.1.0...v0.2.0) (2026-10-10)


### Features

* krizaka-observability, the four starters and releases by release-please ([#5](https://github.com/krizaka/krizaka-platform-kit/issues/5)) ([3cf1788](https://github.com/krizaka/krizaka-platform-kit/commit/3cf1788ff03b274413dd5479cee4054f98b503cb))
* **messaging:** events through the outbox with an AMQP-header envelope, consumer queues with retry then DLQ, Jackson 3 JSON ([#4](https://github.com/krizaka/krizaka-platform-kit/issues/4)) ([ceeed95](https://github.com/krizaka/krizaka-platform-kit/commit/ceeed95cf7ca2fb9f75ed2cb56c96d7a16c9a777))
* **web:** krizaka-web — Problem Details, request correlation, Jackson 3 defaults, cursor pagination, declared CORS ([#3](https://github.com/krizaka/krizaka-platform-kit/issues/3)) ([78f17f6](https://github.com/krizaka/krizaka-platform-kit/commit/78f17f68c540ae1c3b6451d1ba1eec50ea7917dd))


### Bug Fixes

* **release:** release-please skips the -SNAPSHOT pull request ([#7](https://github.com/krizaka/krizaka-platform-kit/issues/7)) ([528171b](https://github.com/krizaka/krizaka-platform-kit/commit/528171b4405257b56b2101eeb9e170f47603cc73))

## Before release-please

Written by hand, in the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.

### Unreleased when release-please took over (shipped in the first release below it)

#### Added

- `krizaka-observability` — `KrizakaObservabilityProperties` (`krizaka.observability.product|service|version`,
  required: a service that does not name itself fails at startup with every missing key),
  `KrizakaObservabilityAutoConfiguration` (`product`, `service`, `version` as common tags of every meter, where
  Micrometer is present) and `KrizakaObservabilityEnvironmentPostProcessor` (lowest priority:
  `logging.structured.format.console=ecs`, `management.endpoints.web.exposure.include=health,info,prometheus`,
  `management.tracing.sampling.probability=0.1`).
- Starters — one dependency per capability, each an empty jar (a POM) with an integration test that starts an
  application on it alone: `krizaka-spring-boot-starter-web`, `-security`, `-rabbitmq`, `-observability`
  (parent `krizaka-spring-boot-starters`).
- `examples/krizaka-starters-example`: a service on the four starters with a 12-line `application.yml`, tested on real
  PostgreSQL and RabbitMQ; never published.
- Releases by release-please (`release-please-config.json`, `.github/workflows/release-please.yml`) and Conventional
  Commits pull request titles checked by `.github/workflows/commitlint.yml`.
- `krizaka-web` — the HTTP baseline of a Spring MVC service:
  - `ProblemDetailsAdvice`: RFC 9457 Problem Details for every error — `DomainException` (abstract; `NotFoundException`,
    `ConflictException`, `ForbiddenException`, `ValidationException`) → its 4xx with `type`, `code` and `requestId`;
    bean validation → `422` with `errors[]`; Spring MVC's own failures keep their status and gain `requestId`;
    `@ResponseStatus` honoured; Spring Security's exceptions left to Spring Security; anything else → `500` without
    its message.
  - `CorrelationId` (MDC scope, `current()`, `currentOrNew()`) and `CorrelationIdFilter` (`X-Request-Id` accepted when
    safe, created otherwise, echoed, first in the chain).
  - `KrizakaJsonDefaults`: Jackson 3 ISO-8601 dates, `NON_NULL`, tolerant reader; `spring.jackson.*` still wins.
  - `Cursor` / `CursorPage<T>`: keyset pagination with an opaque base64url cursor, page size ≤ 100,
    `InvalidCursorException` (`400 invalid-cursor`) for anything `encode` did not produce.
  - Declared CORS (`krizaka.web.cors.allowed-origins`, exact origins only) for Spring Security and Spring MVC alike.
  - `KrizakaWebProperties` (`krizaka.web.*`), `KrizakaWebAutoConfiguration` (also applied to `@WebMvcTest` slices) and
    `KrizakaWebEnvironmentPostProcessor` (`spring.mvc.problemdetails.enabled=true`, lowest priority).
- `krizaka-messaging` — events, consumer topology, retry and dead letters:
  - `EventPublisher` / `OutboxEventPublisher`: one transactional call writes the event to the context's outbox, body =
    the bare event serialized by the application's Jackson 3 `JsonMapper`, `messageId` chosen at write time, envelope in
    AMQP headers (`EventHeaders`: `kz-type`, `kz-version`, `kz-producer`, `kz-correlation-id`, `kz-occurred-at`).
    `EventPublisherAutoConfiguration` builds it lazily and fails at startup, with the reason, when it is injected
    without `krizaka.messaging.producer` or without exactly one `OutboxStore`.
  - `NewOutboxMessage` and `OutboxStore.append(NewOutboxMessage)`; `OutboxMessage` carries `headers`; the relay
    publishes them with the `messageId`, `application/json` and a timestamp. The recommended outbox table gains
    `headers jsonb`.
  - `KrizakaQueues.consumer(exchanges, queue, routingKeys...)`: quorum queue + `<queue>.dlq`, bound by convention.
  - `KrizakaRabbitAutoConfiguration`: `JacksonJsonMessageConverter` over the application's `JsonMapper`
    (`alwaysConvertToInferredType`), and a `rabbitListenerContainerFactory` keeping Spring Boot's listener settings with
    a stateless retry (`ConsumerRetryProperties`, `krizaka.messaging.retry.*`: 5 / 500 ms / ×2 / 10 s) and
    `DeadLetterQueueRecoverer` (republish to `<queue>.dlq` with the original headers); `defaultRequeueRejected=false`.
    Each bean yields to the application's own; the factory also yields to an application that declared Spring Boot's
    retry; `krizaka.messaging.consumer.enabled=false` turns it all off.
  - `MessagingRoundTripIT` on real PostgreSQL and RabbitMQ (`krizaka-test-support`).

#### Changed

- The kit has its own version (SemVer per repository); the `krizaka-parent` it inherits and `krizaka-test-support`
  (`krizaka-build.version`) follow a `krizaka-build` release explicitly.
- `OutboxStore.append` is a **default** method that throws `UnsupportedOperationException`: existing stores keep
  compiling and relaying; implement it to publish through `EventPublisher`. The six-argument `OutboxMessage`
  constructor is kept (no headers).

### Version 0.1.0

#### Added

- `krizaka-security`
  - `SessionJwtProperties` (`krizaka.security.jwt.secret`, 32-character minimum) and `SessionJwtAutoConfiguration`:
    HS256 `JwtDecoder` and a `roles`-claim `JwtAuthenticationConverter` with no authority prefix.
  - `SecurityBaseline.apply`: stateless chain, CORS preflight / health / info / error open, `/internal/v1/**` requires
    `SERVICE`, the service's rules, then `anyRequest().authenticated()`.
  - `ServiceTokenProvider`: five-minute HS256 `SERVICE` tokens, JDK only.
- `krizaka-messaging`
  - `MessageDedup` with `JdbcMessageDedup` (one `INSERT`, unique-key arbitration) and `InMemoryMessageDedup`, selected by
    `krizaka.messaging.dedup.store`; `purgeClaimedBefore` for claim retention.
  - `OutboxStore` SPI, `OutboxRelay` and its self-contained scheduler, configured by `krizaka.messaging.outbox.*`.
