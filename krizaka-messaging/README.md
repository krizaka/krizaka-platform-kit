# krizaka-messaging

**At-least-once messaging on RabbitMQ, written once:** an event is published by one transactional line, a consumer queue
is declared by one line, a failing listener is retried with back-off and then dead-lettered, and a redelivered message
is processed once.

```xml
<dependency>
    <groupId>com.krizaka</groupId>
    <artifactId>krizaka-messaging</artifactId>
</dependency>
```

RabbitMQ (`spring-boot-starter-amqp`), JDBC (`spring-boot-starter-jdbc`) and Jackson 3 are **optional** dependencies:
bring the ones you use. Every auto-configuration is guarded, so an application without them starts.

## Publish an event

```yaml
krizaka:
  messaging:
    producer: krizaka-users          # stamped on every event (kz-producer) — required to inject an EventPublisher
    exchanges:
      events: platform.events        # defaults: krizaka.events / krizaka.dlx
      dead-letter: platform.dlx
```

```java
@Transactional
public User register(NewUser input) {
  User user = users.save(input);
  events.publish("evt.user.registered", 1, new UserRegistered(user.id(), user.email()));
  return user;
}
```

`EventPublisher.publish` writes one row to **your** outbox, in **your** transaction: the event is committed or rolled
back with the state change it announces, and the relay publishes it after the commit. The body is the bare event as
JSON (the application's `JsonMapper`); the envelope travels in AMQP headers, so consumers that read only the body see
no change:

| Header | Value |
|:---|:---|
| `kz-type` | the routing key, `evt.user.registered` |
| `kz-version` | the contract version, `1` |
| `kz-producer` | `krizaka.messaging.producer` |
| `kz-correlation-id` | the `requestId` of the request that produced it (krizaka-web's MDC), or a new UUID |
| `kz-occurred-at` | when it happened, ISO-8601 UTC |

The AMQP `messageId` is a UUID chosen **when the row is written**: a relay that crashes after publishing republishes
the same id, and the consumer's `MessageDedup` drops the copy. The message also carries `content_type:
application/json`, persistent delivery and a publish `timestamp`.

The publisher is built the first time it is injected. Injecting one without `krizaka.messaging.producer`, or with no
(or several) `OutboxStore` beans, fails at startup with the reason; a service that never publishes declares nothing.

## Consume a queue

```java
@Bean
Declarables userEvents(MessagingExchanges exchanges) {
  return KrizakaQueues.consumer(exchanges, "krizaka.notifications.user-events",
      "evt.user.registered", "evt.user.verified");
}

@RabbitListener(queues = "krizaka.notifications.user-events")
void onRegistered(UserRegistered event, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
  if (!dedup.claim("notifications.user-events", messageId)) return;   // already processed
  try { welcome(event); } catch (RuntimeException e) { dedup.release("notifications.user-events", messageId); throw e; }
}
```

`KrizakaQueues.consumer` declares the events (topic) and dead-letter (direct) exchanges, the **quorum** queue bound to
each routing key, and `<queue>.dlq`. The listener receives the type of its parameter — a tolerant copy of the
producer's contract, never a class named in a header.

The kit contributes the default `rabbitListenerContainerFactory`: Spring Boot's `spring.rabbitmq.listener.simple.*`
settings (concurrency, prefetch, acknowledgement, observation), then a stateless retry, then the message is republished
to `<queue>.dlq` **with its original headers** plus `x-exception-message`, `x-exception-stacktrace`,
`x-original-exchange` and `x-original-routingKey`. A rejected message is never requeued.

```yaml
krizaka:
  messaging:
    retry:            # defaults
      max-attempts: 5 # the first delivery included
      initial: 500ms
      multiplier: 2
      max: 10s
    consumer:
      enabled: true   # false: no converter, no container factory — configure listeners yourself
```

Your own `MessageConverter` or `rabbitListenerContainerFactory` bean wins. An application that already declares Spring
Boot's retry (`spring.rabbitmq.listener.simple.retry.enabled=true`) keeps Spring Boot's factory: its queues may
dead-letter under another routing key, and two retry policies for one listener would leave one silently unused. Move
to `krizaka.messaging.retry.*` (and `KrizakaQueues`) to adopt the kit's.

## Conventions

| What | Convention | Example |
|:---|:---|:---|
| Event routing key | `evt.<aggregate>.<event>` | `evt.user.registered` |
| Incompatible version | a new routing key, `.v<n>`, and `kz-version: n` | `evt.user.registered.v2` |
| Consumer queue | `<service>.<usage>` | `krizaka.notifications.user-events` |
| Dead-letter queue | `<queue>.dlq` | `krizaka.notifications.user-events.dlq` |
| Dedup consumer name | the queue, or the use case | `notifications.user-events` |

A compatible change (a new optional field) keeps the routing key and the version: consumers ignore what they do not
know. An incompatible one (a field removed, renamed or retyped) is published under `evt.x.y.v2` **next to** `evt.x.y`
until every consumer has moved; no shared jar of event classes, ever — each consumer keeps its own copy of the
contract.

## Recommended outbox schema

The kit never creates a table: each context owns its outbox and implements `OutboxStore` over it.

```sql
CREATE TABLE wallet_outbox (
    id              UUID         PRIMARY KEY,
    exchange        VARCHAR(100) NOT NULL,
    routing_key     VARCHAR(255) NOT NULL,
    message_id      VARCHAR(255) NOT NULL UNIQUE,
    payload         JSONB        NOT NULL,
    headers         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX wallet_outbox_pending ON wallet_outbox (next_attempt_at) WHERE published_at IS NULL;
```

```java
@Component
class WalletOutbox implements OutboxStore {
  public void append(NewOutboxMessage m) {
    jdbc.update("""
        INSERT INTO wallet_outbox (id, exchange, routing_key, message_id, payload, headers)
        VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)""",
        UUID.randomUUID(), m.exchange(), m.routingKey(), m.messageId(),
        new String(m.body(), UTF_8), json.writeValueAsString(m.headers()));
  }
  public List<OutboxMessage> lockPendingBatch(int batchSize) {
    return jdbc.query("""
        SELECT id, exchange, routing_key, message_id, payload::text, headers::text, attempts FROM wallet_outbox
        WHERE published_at IS NULL AND next_attempt_at <= now()
        ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED""", this::row, batchSize);
  }
  // markPublished, recordFailure (your back-off), purgePublishedBefore
}
```

**`lockPendingBatch` must claim its rows** (`FOR UPDATE SKIP LOCKED`), or two instances publish the same row. A store
written before `append` existed still compiles and relays: the default `append` throws
`UnsupportedOperationException`, so only an `EventPublisher` over it fails, naming the method to implement.

The relay (`krizaka.messaging.outbox.{enabled, poll-interval, batch-size, purge-interval, retention}`) runs one
transaction per batch on its own thread, marks each published row, backs failures off and purges after the retention
window.

## Process each message once

```yaml
krizaka:
  messaging:
    dedup:
      store: jdbc        # or `memory` for a service without a database
```

`MessageDedup.claim` is one `INSERT` arbitrated by the primary key; `release` gives the claim back when the handler
fails, so the redelivery is processed instead of dropped. The table:

```sql
CREATE TABLE processed_messages (
    consumer     VARCHAR(255) NOT NULL,
    message_id   VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
```

## Tested against

`MessagingRoundTripIT` runs the whole path on a real PostgreSQL and RabbitMQ (`krizaka-test-support`'s
`AbstractContainerIntegrationTest`): publish → outbox → relay → listener with the five headers; a rolled-back
transaction publishes nothing; a listener failing twice is retried with back-off; a listener that always fails ends in
`<queue>.dlq` with its envelope; a replayed `messageId` is refused by the JDBC deduplication.
