<!-- krizaka-header -->
<div align="center">

<img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/krizaka.svg" alt="Krizaka" width="72">

# Krizaka Platform Kit

**The cross-cutting code every Spring Boot service writes — written once, and right.**

Stateless JWT security with one baseline, service-to-service tokens, idempotent message consumption and a
transactional outbox relay. Spring Boot auto-configurations: add the dependency, declare the properties.

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
| [`krizaka-messaging`](krizaka-messaging) | `com.krizaka:krizaka-messaging` | **Idempotent consumption** (atomic claim + release on failure) and the **transactional outbox relay** for RabbitMQ |

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

### Process each message once — and a failed one again

```yaml
krizaka:
  messaging:
    dedup:
      store: jdbc        # or `memory` for a service without a database
```

```java
@RabbitListener(queues = "billing.settlements")
void onSettlement(Message message) {
  String id = message.getMessageProperties().getMessageId();
  if (!dedup.claim("billing.settlement", id)) {
    return;                                   // already processed
  }
  try {
    settle(message);
  } catch (RuntimeException e) {
    dedup.release("billing.settlement", id);  // the redelivery is processed, not dropped
    throw e;
  }
}
```

Claims are only useful while a redelivery is possible: call `dedup.purgeClaimedBefore(Instant.now().minus(Duration.ofDays(7)))`
from your housekeeping job to keep the table small.

`claim` is one `INSERT` arbitrated by the primary key — never a read followed by a write, which two overlapping
deliveries both pass. The store is **declared**, never inferred: no property, no bean, and `store=jdbc` without a
database fails at startup instead of quietly falling back to memory. The JDBC table:

```sql
CREATE TABLE processed_messages (
    consumer     VARCHAR(255) NOT NULL,
    message_id   VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
```

### Publish what you committed, exactly once per success

Write your events to an outbox table in the same transaction as the state change, and implement `OutboxStore` over it:

```java
@Component
class WalletOutbox implements OutboxStore {
  public List<OutboxMessage> lockPendingBatch(int batchSize) {
    return jdbc.query("""
        SELECT id, exchange, routing_key, message_id, payload::text, attempts FROM wallet_outbox
        WHERE published_at IS NULL AND next_attempt_at <= now()
        ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED""", this::row, batchSize);
  }
  // markPublished, recordFailure (your back-off), purgePublishedBefore
}
```

Every `OutboxStore` bean gets a relay: one transaction per batch, each row published as a persistent JSON message with
its `messageId`, marked on success, backed off on failure, purged after the retention window. It runs on its own thread
(no `@EnableScheduling` needed) and is tuned with `krizaka.messaging.outbox.{enabled, poll-interval, batch-size,
purge-interval, retention}`. **`lockPendingBatch` must claim its rows** (`FOR UPDATE SKIP LOCKED` on PostgreSQL), or two
instances publish the same row.

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
