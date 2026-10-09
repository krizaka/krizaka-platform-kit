<!-- krizaka-header -->
<div align="center">

<img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/krizaka.svg" alt="Krizaka" width="72">

# Krizaka Platform Kit

**The cross-cutting code every Spring Boot service writes — written once, and right.**

Stateless JWT security with one baseline, service-to-service tokens, idempotent message consumption, a
transactional outbox relay and one HTTP error format. Spring Boot auto-configurations: add the dependency, declare the
properties.

[![CI](https://github.com/krizaka/krizaka-platform-kit/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/krizaka-platform-kit/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.krizaka/krizaka-security?color=3b82f6&label=maven%20central)](https://central.sonatype.com/namespace/com.krizaka)
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

Requires Java 21 and Spring Boot 4.0.

## Install

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.krizaka</groupId>
            <artifactId>krizaka-bom</artifactId>
            <version>0.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.krizaka</groupId>
        <artifactId>krizaka-security</artifactId>
    </dependency>
    <dependency>
        <groupId>com.krizaka</groupId>
        <artifactId>krizaka-messaging</artifactId>
    </dependency>
    <dependency>
        <groupId>com.krizaka</groupId>
        <artifactId>krizaka-web</artifactId>
    </dependency>
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

## Build

```bash
./mvnw verify                        # tests, formatting, coverage
./mvnw verify -Prelease -Dgpg.skip   # + the sources and javadoc jars Maven Central requires
```

It inherits [`krizaka-parent`](https://github.com/krizaka/krizaka-build): build it first, or let CI do it.

## Contributing

Issues and pull requests are welcome — see the organisation's
[contributing guide](https://github.com/krizaka/.github/blob/main/CONTRIBUTING.md) and
[security policy](https://github.com/krizaka/.github/blob/main/SECURITY.md).

## License

[Apache License 2.0](LICENSE) © 2026 Krizaka
