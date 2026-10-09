<!-- krizaka-header -->
<div align="center">

<img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/krizaka.svg" alt="Krizaka" width="72">

# Krizaka Notifications

**Publish an event; the right message reaches the right channel.**

E-mail, SMS and webhooks behind one delivery port, driven by your domain events or by explicit requests over AMQP —
templated, localised, idempotent, and never silently dropped.

[![CI](https://github.com/krizaka/krizaka-notifications/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/krizaka-notifications/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.krizaka/krizaka-notifications-api?color=3b82f6&label=maven%20central)](https://central.sonatype.com/namespace/com.krizaka)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

[Open source at Krizaka](https://www.krizaka.com/en/open-source) · [Website](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

Built for and used by [Orazaka](https://github.com/krizaka/orazaka); usable by any application that speaks AMQP.

## What it does

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

### Inputs (AMQP, the platform's events topic exchange)

| Routing key | Queue | What is sent |
|:---|:---|:---|
| `evt.user.*` (`evt.user.registered`) | `krizaka.notifications.user-events` | verification e-mail |
| `evt.password.*` (`evt.password.reset`) | `krizaka.notifications.password-events` | password-reset e-mail |
| `evt.notification.requested` | `krizaka.notifications.requests` | any `NotificationRequest` (channel, recipient, template, variables) |

Every queue has a `<queue>.dlq`, retries back off exponentially and deliveries are idempotent by
`messageId` (krizaka-messaging).

## Modules

| Artifact | Published | Role |
|:---|:--:|:---|
| `com.krizaka:krizaka-notifications-api` | ✓ | The contract: `NotificationRequest`, `Channel` and the routing constants. Depend on this to request a notification. |
| `krizaka-notifications-service` | — | The Spring Boot host (port `8097`): templates, the `DeliveryClient` port and its adapters. Built from source. |

## Request a notification

```xml
<dependency>
    <groupId>com.krizaka</groupId>
    <artifactId>krizaka-notifications-api</artifactId>
    <version>0.1.0</version>
</dependency>
```

Publish a `NotificationRequest` (channel, recipient, template, variables) as JSON on the events exchange with the
routing key `evt.notification.requested` and a `messageId` — a redelivery is then sent once.

## Run the service

```bash
./mvnw -pl krizaka-notifications-service -am spring-boot:run
```

| Property | Environment | Default |
|:---|:---|:---|
| `krizaka.notifications.from-address` | `NOTIFICATIONS_FROM` | `Krizaka <no-reply@krizaka.com>` |
| `krizaka.notifications.template-location` | `NOTIFICATIONS_TEMPLATES` | `classpath:templates/` |
| `krizaka.notifications.links.verify-email` / `.reset-password` | `NOTIFICATIONS_VERIFY_EMAIL_URL` / `NOTIFICATIONS_RESET_PASSWORD_URL` | `http://localhost:3000/…?token={token}` |
| `krizaka.notifications.twilio.*` | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | empty — SMS unavailable |
| `krizaka.notifications.webhook.allowed-hosts` | `NOTIFICATIONS_WEBHOOK_ALLOWED_HOSTS` | empty — webhooks unavailable |
| `spring.mail.*` | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | `localhost:1025` (Mailpit) |

RabbitMQ is required; no database.

> **Messaging.** The service binds its queues (`krizaka.notifications.*`) to the exchanges of the platform it runs on:
> `krizaka.messaging.exchanges.events` / `.dead-letter` (`EVENTS_EXCHANGE` / `DLX_EXCHANGE`, defaults `krizaka.events` /
> `krizaka.dlx`).

## Build

```bash
./mvnw verify                        # tests and governance
./mvnw verify -Prelease -Dgpg.skip   # + the sources and javadoc jars Maven Central requires
```

It inherits [`krizaka-parent`](https://github.com/krizaka/krizaka-build) and uses
[`krizaka-platform-kit`](https://github.com/krizaka/krizaka-platform-kit): build those first, or let CI do it. JDK 21.

## Contributing

Issues and pull requests are welcome — see the organisation's
[contributing guide](https://github.com/krizaka/.github/blob/main/CONTRIBUTING.md) and
[security policy](https://github.com/krizaka/.github/blob/main/SECURITY.md).

## License

[Apache License 2.0](LICENSE) © 2026 Krizaka
