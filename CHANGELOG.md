# Changelog

All notable changes to this repository are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).
Every Krizaka JVM artifact is released at the same version.

## [Unreleased]

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
