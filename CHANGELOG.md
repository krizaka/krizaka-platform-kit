# Changelog

All notable changes to this repository are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).
Every Krizaka JVM artifact is released at the same version.

## [Unreleased]

### Added

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

### Changed

- `OutboxStore.append` is a **default** method that throws `UnsupportedOperationException`: existing stores keep
  compiling and relaying; implement it to publish through `EventPublisher`. The six-argument `OutboxMessage`
  constructor is kept (no headers).

## [0.1.0]

### Added

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
