<!-- krizaka-header -->
<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/krizaka-dark.svg">
  <img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/krizaka-light.svg" alt="Krizaka" width="96">
</picture>

# Krizaka Platform Kit

**The cross-cutting code every Spring Boot service writes — written once, and right.**

Stateless JWT security with one baseline, service-to-service tokens, idempotent message consumption, a
transactional outbox relay and one HTTP error format. Spring Boot auto-configurations: add the dependency, declare the
properties.

[![CI](https://github.com/krizaka/krizaka-platform-kit/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/krizaka-platform-kit/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/krizaka/krizaka-platform-kit/badge)](https://scorecard.dev/viewer/?uri=github.com/krizaka/krizaka-platform-kit)
[![Maven Central](https://img.shields.io/maven-central/v/com.krizaka/krizaka-security?color=196df5&label=maven%20central)](https://central.sonatype.com/namespace/com.krizaka)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

[Open source at Krizaka](https://www.krizaka.com/en/open-source) · [Website](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

## Why it exists

Measured across the services of [Orazaka](https://github.com/krizaka/orazaka), this code had been written five to seven
times — and the copies had **three live bugs** between them: a deduplication that let two concurrent deliveries both
through, one that silently dropped a message after a failure, an outbox relay that published rows without claiming them,
and security chains that left a CORS preflight or the error page behind a login. None of it was business logic. It is
infrastructure every service needs and every service gets subtly wrong, so it lives here, once, with the invariants
tested.

| Module | Artifact | What it carries |
|:---|:---|:---|
| [`krizaka-security`](krizaka-security) | `com.krizaka:krizaka-security` | HS256 session-token verification, `roles`-claim authorities, the **security baseline**, the `SERVICE` token for `/internal/v1/**` |
| [`krizaka-messaging`](krizaka-messaging) | `com.krizaka:krizaka-messaging` | **Events through the outbox** with their envelope in AMQP headers, consumer queues with **retry then DLQ**, **idempotent consumption** and the **transactional outbox relay** for RabbitMQ |
| [`krizaka-web`](krizaka-web) | `com.krizaka:krizaka-web` | **RFC 9457 Problem Details** with stable codes, `X-Request-Id` → MDC, Jackson 3 defaults, cursor pagination, declared CORS |
| [`krizaka-observability`](krizaka-observability) | `com.krizaka:krizaka-observability` | **Required service names** (product, service, version) as common metric tags, ECS JSON logs, health/info/prometheus exposed, 10 % trace sampling — defaults at the lowest priority |
| [`krizaka-idempotency`](krizaka-idempotency) | `com.krizaka:krizaka-idempotency` | **HTTP `Idempotency-Key`**: an action runs at most once per key, a retry replays its response (`Idempotent-Replayed: true`), the same key for another request is a `422`; PostgreSQL store in the action's transaction — promoted from Orazaka |
| [`krizaka-app-config`](krizaka-app-config) | `com.krizaka:krizaka-app-config` | **App version compatibility**: a public cached `GET /api/v1/app-config` (minimum and latest version, update mode, maintenance, contract range, store URL, flags, localized message) read from database parameters, **`426 Upgrade Required`** below the minimum, N-1 contract compatibility with `Deprecation`/`Sunset`, an audited admin API with rollback |

Requires Java 21 and Spring Boot 4.0.

## Starters — one dependency per capability

A starter is a POM: the kit module and the Spring Boot starters the capability needs, nothing else. A product declares
capabilities, never the Spring list behind them; a Spring Boot upgrade changes the starters, not the product's POM.

| Starter | Brings |
|:---|:---|
| `com.krizaka:krizaka-spring-boot-starter-web` | `krizaka-web` + `spring-boot-starter-webmvc` + `-validation` + `-actuator` |
| `com.krizaka:krizaka-spring-boot-starter-security` | `krizaka-security` + `spring-boot-starter-security` + `-oauth2-resource-server` |
| `com.krizaka:krizaka-spring-boot-starter-rabbitmq` | `krizaka-messaging` + `spring-boot-starter-amqp` + `-jdbc` + `-jackson` (add your JDBC driver) |
| `com.krizaka:krizaka-spring-boot-starter-observability` | `krizaka-observability` + `spring-boot-starter-actuator` + OpenTelemetry tracing (`spring-boot-micrometer-tracing-opentelemetry`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`) + `micrometer-registry-prometheus` |
| `com.krizaka:krizaka-spring-boot-starter-idempotency` | `krizaka-idempotency` + `krizaka-spring-boot-starter-web` + `spring-boot-starter-jdbc` (add your JDBC driver) |
| `com.krizaka:krizaka-spring-boot-starter-app-config` | `krizaka-app-config` + `spring-boot-starter-webmvc` + `-jdbc` |

The whole configuration of a service on the four starters — everything else is a kit default you can override
([`examples/krizaka-starters-example`](examples/krizaka-starters-example), started on real PostgreSQL and RabbitMQ by
its integration test):

```yaml
spring:
  application.name: orazaka-conversation-service
  datasource.url: ${DATABASE_URL}
  rabbitmq.addresses: ${RABBITMQ_URL}
krizaka:
  observability: { product: orazaka, service: conversation, version: "@project.version@" }
  security.jwt.secret: ${KRIZAKA_JWT_SECRET}
  messaging:
    exchanges: { events: orazaka.events, dead-letter: orazaka.dlx }
    dedup.store: jdbc
    outbox.enabled: true
  web.cors.allowed-origins: [ "https://app.orazaka.com" ]
```

## Install

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.krizaka</groupId>
            <artifactId>krizaka-bom</artifactId>
            <version>0.2.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.krizaka</groupId>
        <artifactId>krizaka-spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.krizaka</groupId>
        <artifactId>krizaka-spring-boot-starter-observability</artifactId>
    </dependency>
    <!-- … or a module alone (krizaka-security, krizaka-messaging, krizaka-web, krizaka-observability)
         in an application that brings its own Spring stack: a batch without web, a test without Rabbit. -->
</dependencies>
```

## krizaka-security

### Verify session tokens locally

```yaml
krizaka:
  security:
    jwt:
      secret: ${IDENTITY_JWT_SECRET}   # ≥ 32 characters, or the service refuses to start
```

With the secret set, the auto-configuration contributes a `JwtDecoder` (HS256 only, this key only) and a
`JwtAuthenticationConverter` that maps the `roles` claim to authorities **with no prefix** — `ROLE_USER`, `ROLE_ADMIN`
and `SERVICE` arrive as they were issued. Both back off if you declare your own.

### One baseline for every filter chain

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter roles) throws Exception {
  return SecurityBaseline.apply(http, auth -> auth.requestMatchers("/api/v1/catalogue/**").permitAll())
      .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(roles)))
      .build();
}
```

`SecurityBaseline.apply` makes the chain stateless (CSRF off — token-only APIs), opens `OPTIONS /**` (a CORS preflight
never carries a token), `/actuator/health`, `/actuator/info` and `/error`, reserves `/internal/v1/**` for the `SERVICE`
authority, runs **your** rules, and ends with `anyRequest().authenticated()` — nothing is open by omission. How tokens
are read (JWT, opaque, filters, CORS) stays yours.

### Call another service's internal surface

```java
ServiceTokenProvider tokens = new ServiceTokenProvider(secret, "billing-client");
RestClient.builder()
    .baseUrl(billingUrl)
    .requestInitializer(request -> request.getHeaders().setBearerAuth(tokens.token()))
    .build();
```

A five-minute HS256 token with `roles: ["SERVICE"]`, minted per call. `ServiceTokenProvider` needs nothing but the JDK:
the Spring Security dependencies of this module are optional, so a thin HTTP client can mint tokens without inheriting a
security stack. Any process holding the secret can mint `SERVICE` — keep the secret where it is needed.

## krizaka-messaging

### Publish an event in one transactional line

```yaml
krizaka:
  messaging:
    producer: krizaka-users
```

```java
@Transactional
public User register(NewUser input) {
  User user = users.save(input);
  events.publish("evt.user.registered", 1, new UserRegistered(user.id(), user.email()));
  return user;
}
```

The event is a row of your outbox, committed with the state change; the relay publishes it afterwards with a
`messageId` chosen at write time and its envelope (`kz-type`, `kz-version`, `kz-producer`, `kz-correlation-id`,
`kz-occurred-at`) in AMQP headers — the body stays the bare event.

### Consume a queue declared in one line

```java
@Bean
Declarables userEvents(MessagingExchanges exchanges) {
  return KrizakaQueues.consumer(exchanges, "krizaka.notifications.user-events", "evt.user.registered");
}
```

A quorum queue and its `<queue>.dlq`; the kit's listener container retries a failing listener with back-off
(`krizaka.messaging.retry.*`, 5 attempts from 500 ms by default) and then dead-letters it with its headers. Pair it
with `MessageDedup` (`krizaka.messaging.dedup.store=jdbc|memory`): `claim` is one `INSERT`, `release` gives the claim
back when the handler fails, so a redelivery is processed once and a failure is never silently dropped.

```java
@RabbitListener(queues = "krizaka.notifications.user-events")
void onRegistered(UserRegistered event, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
  dedup.processOnce("notifications.user-events", messageId, () -> welcome(event));
}
```

Every `OutboxStore` bean gets a relay; **`lockPendingBatch` must claim its rows** (`FOR UPDATE SKIP LOCKED`). The
recommended outbox schema, the event versioning rules and the naming conventions:
[krizaka-messaging/README.md](krizaka-messaging/README.md).

## krizaka-web

### One error format for every service

```java
throw new ConflictException("auction-closed", "The auction closed at 18:00.");
```

```json
{
  "type": "https://krizaka.com/problems/auction-closed",
  "title": "auction closed",
  "status": 409,
  "detail": "The auction closed at 18:00.",
  "code": "auction-closed",
  "requestId": "0b6c3f1e-6a52-4c4e-9a43-61f0f1f2a1d7"
}
```

A `DomainException` (`NotFoundException`, `ConflictException`, `ForbiddenException`, `ValidationException`, or your
own 4xx) is the only exception that leaves as a 4xx; bean validation is a `422` with an `errors` array; anything
unexpected is a `500` whose message is logged with the `requestId`, never sent. `X-Request-Id` is accepted or created,
sent back and put in the MDC; Jackson writes ISO dates and omits nulls; `Cursor` / `CursorPage` page by key with an
opaque token; CORS opens only the origins listed in `krizaka.web.cors.allowed-origins`. The full contract:
[krizaka-web/README.md](krizaka-web/README.md).

## krizaka-idempotency

A client whose connection dropped after the server accepted a paid request cannot tell whether it ran; retrying blind
pays twice. With a key, the retry is safe:

```yaml
krizaka:
  idempotency.store: jdbc        # or memory (tests); the table: com/krizaka/idempotency/schema-postgresql.sql
```

```java
@PostMapping("/api/v1/orders")
ResponseEntity<Order> create(@RequestBody NewOrder order, @AuthenticationPrincipal Jwt caller,
    @RequestHeader(value = IdempotencyKeys.KEY_HEADER, required = false) String key) {
  return idempotency
      .execute(new IdempotentRequest("orders.create", caller.getSubject(), key, order),
          Order.class, () -> orders.create(order))
      .toResponse(HttpStatus.CREATED);
}
```

The first request runs the action and stores its response **in the action's transaction** (a failed action records
nothing, its retry runs for real); a retry with the same key and request replays it with `Idempotent-Replayed: true`;
the same key for another request is `422 idempotency-key-reused`; two concurrent requests with one key take turns on the
key's primary key — the second waits, then replays. Keys live `krizaka.idempotency.ttl` (24 h) and are purged. Proven on
PostgreSQL by `JdbcIdempotencyIT` and, over HTTP, by [`examples/krizaka-idempotency-example`](examples/krizaka-idempotency-example).
Promoted from Orazaka's run starts (ADR-083).

## krizaka-app-config

A mobile or desktop app outlives the API it calls. The app sends `X-Client-Platform`, `X-Client-Version` and
`X-Contract-Version`; the service answers `426 Upgrade Required` (Problem Details carrying the `appConfig` payload) below
the minimum, keeps the previous contract working with `Deprecation` / `Sunset`, and publishes
`GET /api/v1/app-config?platform=ios&version=1.4.0` for the launch and resume checks. Every value is a database
parameter with safety bounds, a reason and an audit trail; the contract is shared with the TypeScript package
`@krizaka/app-config` through one JSON schema and one fixture. Full contract and wiring:
[krizaka-app-config/README.md](krizaka-app-config/README.md).

## krizaka-observability

```yaml
krizaka:
  observability: { product: acme, service: orders, version: "@project.version@" }
```

The three names are required — a service that cannot say who it is does not start, and the error lists what is
missing. Every meter is tagged `product`, `service`, `version`. At the lowest priority, so `application.yml` still wins:
`logging.structured.format.console=ecs`, `management.endpoints.web.exposure.include=health,info,prometheus`,
`management.tracing.sampling.probability=0.1`. The OTLP exporter sends spans once
`management.opentelemetry.tracing.export.otlp.endpoint` (Spring Boot 4) is set.

### What the kit reports

| Where | Name | What |
|:---|:---|:---|
| `/actuator/health` | `outbox` | one entry per relayed store: last success, consecutive failures; **DOWN** at `krizaka.messaging.outbox.failure-threshold` (10) failures in a row. `management.health.outbox.enabled=false` turns it off |
| `/actuator/info` | `krizaka` | `product`, `service`, `version` — the names the meters are tagged with |
| observation (timer + span) | `krizaka.outbox.relay` | one batch, tagged `krizaka.outbox.store` |
| observation (timer + span) | `krizaka.outbox.publish` | one publish, tagged with the exchange and the routing key; a failure is the observation's error |
| observation (timer + span) | `krizaka.messaging.dedup` | one `processOnce`, tagged `krizaka.consumer` and `outcome` = `processed` · `duplicate` · `failed` |

With `spring-boot-starter-actuator` (the observability starter brings it) every observation is a Micrometer timer —
`krizaka_outbox_publish_seconds_count{error="none"}` in Prometheus — and, with tracing, a span.

## Configuration reference

Every `krizaka.*` property is in the metadata Spring Boot's configuration processor generates, with its description and
its default, so an IDE completes and documents it. The full table is on
[java.krizaka.com/configuration](https://java.krizaka.com/configuration), rendered from
[`docs/configuration-metadata.json`](docs/configuration-metadata.json) — which `ConfigurationReferenceTest` (in the
example) holds equal to the generated metadata, and which fails on a property without a description or a default. After
changing a property:

```bash
./mvnw verify -Dkrizaka.reference.write=true   # regenerate docs/configuration-metadata.json, then commit it
```

## Build

```bash
./mvnw verify                        # tests, formatting, coverage
./mvnw verify -Prelease -Dgpg.skip   # + the sources and javadoc jars Maven Central requires
```

It inherits [`krizaka-parent`](https://github.com/krizaka/krizaka-build): build it first, or let CI do it.

## Releasing

Pull request titles are [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `feat!:` —
checked by `commitlint`; the squash merge keeps the title). [release-please](https://github.com/googleapis/release-please)
keeps a release pull request open on `main`; merging it sets the version in every `pom.xml`, writes the CHANGELOG, tags
`v<version>` and dispatches CI on that tag, which signs and uploads to the Central Portal (organisation pipeline,
publication confirmed by hand). Before merging it, the kit's `krizaka-parent` and `krizaka-build.version` must name a
`krizaka-build` release that is on Maven Central — the release refuses any `-SNAPSHOT` parent.

## Contributing

Issues and pull requests are welcome — see the organisation's
[contributing guide](https://github.com/krizaka/.github/blob/main/CONTRIBUTING.md) and
[security policy](https://github.com/krizaka/.github/blob/main/SECURITY.md).

## License

[Apache License 2.0](LICENSE) © 2026 Krizaka

---

<sub>A Krizaka open-source project · [krizaka.com](https://www.krizaka.com) · Apache-2.0</sub>
