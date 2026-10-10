# Changelog

All notable changes to this repository are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).
Every Krizaka JVM artifact is released at the same version.

## [Unreleased]

### Changed

- Built on `krizaka-parent` and `krizaka-platform-kit` **0.2.0** (released on Maven Central); this repository's
  version follows the parent (0.2.0, not yet released).
- The service host is never published to Maven Central: it is excluded from the Central bundle by name
  (`excludeArtifacts`) and ships as a Docker image; the `publishable-artifact-size` enforcer rule fails `verify` when a
  published jar exceeds 5 MB.

## [0.1.0]

First release as a Krizaka building block (formerly `orazaka-notifications`, part of the Orazaka platform).

### Changed

- Coordinates `com.krizaka:krizaka-notifications-api` (was `com.orazaka:orazaka-notification-api`), packages
  `com.krizaka.notifications.*`; the service host is built from source.
- Configuration under `krizaka.notifications.*` (was `orazaka.notifications.*`); the default sender is
  `Krizaka <no-reply@krizaka.com>` — set `NOTIFICATIONS_FROM` to brand it.
- Deliveries deduplicated by `krizaka-messaging` (`store: memory`, the service has no database).
