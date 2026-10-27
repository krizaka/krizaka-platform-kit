# Changelog

Versions follow [Semantic Versioning](https://semver.org/) per repository; the compatible set of every Krizaka JVM
artifact is the one `krizaka-bom` carries. Releases are written by
[release-please](https://github.com/googleapis/release-please) from the Conventional Commits merged on `main`; the
hand-written history is under *Before release-please*.

## [0.3.0](https://github.com/krizaka/krizaka-platform-kit/compare/v0.2.0...v0.3.0) (2026-10-27)


### Features

* **app-config:** krizaka-app-config — app version compatibility, 426 Upgrade Required and N-1 contracts ([#36](https://github.com/krizaka/krizaka-platform-kit/issues/36)) ([7ada484](https://github.com/krizaka/krizaka-platform-kit/commit/7ada4843a92518a1798963d9bd51368145203a5a))
* **config:** every property in the generated metadata with its default, a configurable outbox lease, the configuration reference ([#50](https://github.com/krizaka/krizaka-platform-kit/issues/50)) ([0b95227](https://github.com/krizaka/krizaka-platform-kit/commit/0b9522782d8c23db3a69a761f5bb2e8401a8300f))
* **health:** dedup and appConfig health indicators ([#53](https://github.com/krizaka/krizaka-platform-kit/issues/53)) ([253dadc](https://github.com/krizaka/krizaka-platform-kit/commit/253dadccdb72aae0d72bd47ef551038aa5727613)), closes [#48](https://github.com/krizaka/krizaka-platform-kit/issues/48)
* **idempotency:** HTTP Idempotency-Key as a building block — krizaka-idempotency, its starter and an example app ([#52](https://github.com/krizaka/krizaka-platform-kit/issues/52)) ([2a7181c](https://github.com/krizaka/krizaka-platform-kit/commit/2a7181c2528e21360e14faf7a6f3437f909e85ba))
* **messaging:** MessageDedup.processOnce — claim, run, give the claim back on failure ([#12](https://github.com/krizaka/krizaka-platform-kit/issues/12)) ([2fa3723](https://github.com/krizaka/krizaka-platform-kit/commit/2fa372333144b6284f341075453a17c35afe3213))
* **messaging:** the kit's PostgreSQL outbox store, the dedup purge on a schedule, the schemas with their indexes ([#54](https://github.com/krizaka/krizaka-platform-kit/issues/54)) ([36507f4](https://github.com/krizaka/krizaka-platform-kit/commit/36507f47e353796b791d0567bbef1b75690fdeac))
* **observability:** outbox health indicator, observations of the relay, publish and dedup, krizaka in /actuator/info ([#51](https://github.com/krizaka/krizaka-platform-kit/issues/51)) ([c7be01e](https://github.com/krizaka/krizaka-platform-kit/commit/c7be01ecb86bcb4e746364ab880e9240add4caba))
* **persistence:** the kit's tables migrated at startup — krizaka-persistence and its starter ([#55](https://github.com/krizaka/krizaka-platform-kit/issues/55)) ([e747658](https://github.com/krizaka/krizaka-platform-kit/commit/e747658098459d16714817cd0af2427674cbb31c))
* **ratelimit:** requests per minute per key — module, starter and example app ([#58](https://github.com/krizaka/krizaka-platform-kit/issues/58)) ([b2a2683](https://github.com/krizaka/krizaka-platform-kit/commit/b2a26830403c987ca1e6d93c3afc627d0750288f))
* **webhooks:** Standard Webhooks signing, sending and verification — module, starter and example app ([#56](https://github.com/krizaka/krizaka-platform-kit/issues/56)) ([3776a62](https://github.com/krizaka/krizaka-platform-kit/commit/3776a623b09f0d8a63285f59a648f5e35c93a7fe))


### Bug Fixes

* **deps:** declare commons-compress 1.28.0 (Scorecard Vulnerabilities: Testcontainers' 1.24) ([#43](https://github.com/krizaka/krizaka-platform-kit/issues/43)) ([6ba1a6b](https://github.com/krizaka/krizaka-platform-kit/commit/6ba1a6bab0d962841687925c35321ab8f3da24ac))
* **deps:** security overrides of the Boot BOM (jackson 3.1.7/2.21.7, amqp-client 5.37.0, opentelemetry 1.62.0, tomcat 11.0.25); release-please token read-only at top level ([#34](https://github.com/krizaka/krizaka-platform-kit/issues/34)) ([257d7df](https://github.com/krizaka/krizaka-platform-kit/commit/257d7df6fd28ba07407d2d145111abb07ae353e7))
* **deps:** Spring Boot 4.0.8 in the root pom (parent 0.2.0 from Central still manages 4.0.7: netty, postgresql, webmvc alerts) ([#37](https://github.com/krizaka/krizaka-platform-kit/issues/37)) ([8576c53](https://github.com/krizaka/krizaka-platform-kit/commit/8576c530782d6e11ba5b23d7beff36687429fc4d))
* **messaging:** keep the 0.2.0 bean methods as deprecated overloads (japicmp, Closes [#65](https://github.com/krizaka/krizaka-platform-kit/issues/65)) ([#67](https://github.com/krizaka/krizaka-platform-kit/issues/67)) ([bf619d6](https://github.com/krizaka/krizaka-platform-kit/commit/bf619d62c71c3a5ae2cbe0103a8dfde50e7c3b97))
* **messaging:** the dead letter keeps its stack trace — opt in, Spring AMQP 4.0.5 leaves it out by default ([#25](https://github.com/krizaka/krizaka-platform-kit/issues/25)) ([8910317](https://github.com/krizaka/krizaka-platform-kit/commit/8910317c73a5d1186f9175ec28ab4d6a2e036810))
* **outbox:** publish outside the database transaction with leasing stores ([#45](https://github.com/krizaka/krizaka-platform-kit/issues/45)) ([#46](https://github.com/krizaka/krizaka-platform-kit/issues/46)) ([abed6fb](https://github.com/krizaka/krizaka-platform-kit/commit/abed6fb5b0c9fe0f92554576c3c27bcc553ef94a))
* **test:** governance tests apply only the rules whose subject the library has (no vacuous rule, GOV-006) ([#33](https://github.com/krizaka/krizaka-platform-kit/issues/33)) ([a02e27f](https://github.com/krizaka/krizaka-platform-kit/commit/a02e27f2f9fc3a8ad894285f854fd7b334d42f7a))


### Documentation

* **agents:** no test, not done — every feature and fix ships with its tests ([#23](https://github.com/krizaka/krizaka-platform-kit/issues/23)) ([ee2259c](https://github.com/krizaka/krizaka-platform-kit/commit/ee2259c735b24668cbc4b359c498970825ab309f))
* **config:** the webhooks properties in the configuration reference ([#57](https://github.com/krizaka/krizaka-platform-kit/issues/57)) ([3d9d7fe](https://github.com/krizaka/krizaka-platform-kit/commit/3d9d7fefc7ed0cca313db4e91385844ba8233492))
* **readme:** Krizaka footer and dead tour image removed ([#29](https://github.com/krizaka/krizaka-platform-kit/issues/29)) ([4f4cc35](https://github.com/krizaka/krizaka-platform-kit/commit/4f4cc3507975b678e1097239a6541110d4bd3973))
* **readme:** link the trademark policy under License (.github[#298](https://github.com/krizaka/krizaka-platform-kit/issues/298)) ([#66](https://github.com/krizaka/krizaka-platform-kit/issues/66)) ([5f9ca82](https://github.com/krizaka/krizaka-platform-kit/commit/5f9ca823aa20b6a826e9ef0a051beae675e70031))
* **readme:** OpenSSF Scorecard badge ([#22](https://github.com/krizaka/krizaka-platform-kit/issues/22)) ([9df0f4d](https://github.com/krizaka/krizaka-platform-kit/commit/9df0f4da871c2ce3f88df4068074eb8a67e2a8fd))
* **readme:** SonarCloud badges ([#63](https://github.com/krizaka/krizaka-platform-kit/issues/63)) ([7809bf1](https://github.com/krizaka/krizaka-platform-kit/commit/7809bf113f9c726698b64ddb6e1f7d3c31027da1))
* **readme:** the new Krizaka mark, light and dark, from the organisation profile ([#9](https://github.com/krizaka/krizaka-platform-kit/issues/9)) ([b353f8e](https://github.com/krizaka/krizaka-platform-kit/commit/b353f8e115d301d03f6c20c11aa609dc57179734))

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
