# ADR-001 — Session tokens are signed by their issuer only; service tokens name their caller

- Status: proposed (issue #11)
- Target: krizaka-platform-kit 0.3.0

## Context

`krizaka-security` verifies session tokens with one shared HS256 secret (`krizaka.security.jwt.secret`), and
`ServiceTokenProvider` mints `SERVICE` tokens with the same secret. Whoever can verify can also mint: a user session for
anyone, or a `SERVICE` token that opens `/internal/v1/**` everywhere. One leaked configuration file, or one request
forgery that reads the environment of any service, is a credential for the whole platform. A public kit should not make
every adopter inherit that blast radius.

## Decision

Separate who signs from who verifies.

1. **Session tokens are signed asymmetrically, by their issuer only.** The issuer (`krizaka-users`) signs with ES256 and
   publishes `/.well-known/jwks.json`, keys identified by `kid`. A verifying service sets
   `krizaka.security.jwt.jwk-set-uri` and holds no signing material. `jwk-set-uri` and `secret` are mutually exclusive;
   both set fails at startup. The verifier accepts only the issuer and audience it is configured with, and only
   asymmetric algorithms in this mode (never `none`, never HS256 against a public key).
2. **Service tokens say who calls; the callee decides.** Each caller has its own key pair
   (`krizaka.security.service.key`) and its token carries `sub` = the caller. The callee lists the callers it accepts per
   path (`krizaka.security.internal.allowed-callers`). `SERVICE` is no longer one authority shared by all.
3. **HS256 stays for one minor, with a startup warning, then is removed at 1.0** (SUPPORT policy: N and N-1).

## Refused

- mTLS now: it needs a CA, rotation and a mesh that local-first deployments do not have. It stays the target once a
  mesh exists.
- An external identity provider as a requirement: the blocks must run with nothing but PostgreSQL and RabbitMQ.

## Rotation

The issuer publishes the new key beside the old one, signs with the new `kid`, and removes the old one after the longest
token lifetime. Verifiers cache the JWKS and refresh on an unknown `kid` (rate limited).

## Consequences

- A leaked verifier configuration no longer allows minting. A leaked issuer key does: it is the one secret to protect
  (store outside configuration files).
- Services move one by one; both modes coexist during the minor.

## Slices (each its own pull request, tests first)

1. `krizaka-security`: `jwk-set-uri` mode, a token from another issuer or signed with another key is rejected
   (`ApplicationContextRunner` tests for both modes and the exclusivity).
2. `krizaka-users`: ES256 key, JWKS endpoint, rotation by `kid`.
3. `ServiceTokenProvider` per-caller keys and `allowed-callers` in `SecurityBaseline`.
4. Orazaka services migrate (workspace issue), then HS256 is deprecated.
5. Guard: an ArchUnit rule that a verifier-only module never reads a signing key property.
