# Orazaka Notifications

> Channel-based notification delivery for any Krizaka application: e-mail (SMTP), SMS (Twilio) and webhooks behind one DeliveryClient port, driven by platform events (user registered, password reset) or explicit notification requests over AMQP.

**Layer:** Domain service — reusable by any Krizaka application · **Version:** `1.0.0-SNAPSHOT` · **License:** Apache-2.0 ·
part of the [Orazaka platform](https://github.com/krizaka/orazaka) by [Krizaka](https://krizaka.com)

## What it provides

One service that **sends notifications according to the channel**. Applications never talk to an SMTP
server or an SMS provider directly: they publish an event, this service renders and delivers.

| Channel | Adapter | Configuration | Available when |
|:---|:---|:---|:---|
| `EMAIL` | SMTP (`SmtpDeliveryAdapter`) | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` — defaults to the workspace's Mailpit (`localhost:1025`, inbox on `:8025`) | always |
| `SMS` | Twilio Messages API (`TwilioSmsDeliveryAdapter`) | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | all three are set |
| `WEBHOOK` | HTTP POST of `{template, subject, body}` (`WebhookDeliveryAdapter`) | `NOTIFICATIONS_WEBHOOK_ALLOWED_HOSTS` (comma-separated) | the allow-list is not empty |

A request for a channel that is not available **fails and is dead-lettered** — it is never dropped
silently. Templates live in `src/main/resources/templates/<template>/<locale>.txt` (`Subject:` first
line, `{{variable}}` placeholders, `en` fallback); point `NOTIFICATIONS_TEMPLATES` at a directory to
brand them without a rebuild. Links in the identity e-mails come from
`NOTIFICATIONS_VERIFY_EMAIL_URL` / `NOTIFICATIONS_RESET_PASSWORD_URL` (with a `{token}` placeholder).

### Inputs (AMQP, `orazaka.events` topic exchange)

| Routing key | Queue | What is sent |
|:---|:---|:---|
| `evt.user.*` (`evt.user.registered`) | `orazaka.events.user.notifications` | verification e-mail |
| `evt.password.*` (`evt.password.reset`) | `orazaka.events.password.notifications` | password-reset e-mail |
| `evt.notification.requested` | `orazaka.notifications.requests` | any `NotificationRequest` (channel, recipient, template, variables) |

Every queue has a `<queue>.dlq`, retries back off exponentially and deliveries are idempotent by
`messageId` (AGENTS.md §6).

| Module | Role |
|:---|:---|
| `orazaka-notification-api` | Contract: `NotificationRequest`, `Channel`, routing constants. Depend on this to request a notification. |
| `orazaka-notification-service` | Spring Boot host (port `8097`): templates, the `DeliveryClient` port and its adapters. Stateless — idempotency claims are held in memory (24 h). |

## Use it

Publish a request from any service:

```java
rabbitTemplate.convertAndSend(
    NotificationRouting.EVENTS_EXCHANGE,
    NotificationRouting.NOTIFICATION_REQUESTED,
    new NotificationRequest(Channel.EMAIL, "ada@example.com", "welcome", "fr", Map.of("name", "Ada")));
```

Add a channel by implementing the `DeliveryClient` port (`channel()`, `available()`, `deliver()`) as a
package-private Spring bean in `infrastructure/adapter/delivery` — no other change.

```bash
./mvnw -pl orazaka-notification-service -am spring-boot:run
```

## Position in the platform

| | |
|:---|:---|
| Depends on | [`orazaka-build`](https://github.com/krizaka/orazaka-build) |
| Used by | _no other Orazaka repository._ |
| Workspace path | `orazaka-apps/services/orazaka-notifications` |

## Build

**Inside the Orazaka workspace** (recommended — every dependency is built from source):

```bash
git clone https://github.com/krizaka/orazaka.git && cd orazaka
node scripts/workspace.mjs clone          # clones every repository at its workspace path
./mvnw -f orazaka-apps/services/orazaka-notifications/pom.xml verify
```

**Standalone** — upstream artifacts must be in `~/.m2` (built by the workspace) or resolvable from
GitHub Packages (`https://maven.pkg.github.com/krizaka/<repository>`, see the
[workspace README](https://github.com/krizaka/orazaka#consuming-packages)):

```bash
./mvnw verify
```

Requirements: JDK 21, Docker (Testcontainers integration tests).

## Governance

This repository follows the Orazaka governance contract — [AGENTS.md](https://github.com/krizaka/orazaka/blob/main/AGENTS.md)
in the workspace is normative; the local [AGENTS.md](AGENTS.md) only scopes it to this repository.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
