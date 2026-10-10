# krizaka-notifications — Scope (agent-neutral)

> A Krizaka building block: channel-based notification delivery for any application, its contract published on
> Maven Central as `com.krizaka:krizaka-notifications-api`. Orazaka is its first consumer, not its owner. When this
> repository is cloned inside the Orazaka workspace (`krizaka/krizaka-notifications`), the workspace contract
> ([`krizaka/orazaka/AGENTS.md`](https://github.com/krizaka/orazaka/blob/main/AGENTS.md)) applies as well, and its
> cross-repository rules scan this repository.

## Rules of this repository

- **Depends on Krizaka artifacts only** (`krizaka-build`, `krizaka-platform-kit`) — never on a product;
  `dependsOnNoProduct` fails the build.
- **Stateless.** No database: idempotency claims are held in memory (`krizaka.messaging.dedup.store: memory`).
- **One port per concern**: every channel is a `DeliveryClient` adapter; a channel that is not configured is
  unavailable, and a request for it is dead-lettered — never dropped.
- **Wire names are a contract**: the exchange and queue names in `AmqpConstants` are shared with the producers; changing
  one is a coordinated migration, not a refactor.
- **Configuration** lives under `krizaka.notifications.*`, typed by self-validating records.
- **Services ship as Docker images, never on Maven Central.** Only the libraries (`-api`, `-client`, …) are published; a
  `*-service` host sets `maven.deploy.skip` and is listed in the root POM's `central-publishing-maven-plugin`
  `excludeArtifacts` (the plugin stages every module of the reactor otherwise). The `publishable-artifact-size` enforcer
  rule fails `verify` when a published jar exceeds 5 MB — a runnable (fat) jar never reaches Central.

## Definition of done

1. `./mvnw verify -Prelease -Dgpg.skip` is green (tests, governance, javadoc).
2. Inside the Orazaka workspace, `./mvnw install` from the root is green.
3. [README.md](README.md) and [CHANGELOG.md](CHANGELOG.md) describe the change.
