# krizaka-platform-kit — Scope (agent-neutral)

> Cross-cutting building blocks for Spring Boot services, published on Maven Central as `com.krizaka:krizaka-security`,
> `com.krizaka:krizaka-messaging` and `com.krizaka:krizaka-web`. Orazaka consumes them; so can any application. When
> this repository is cloned inside the Orazaka workspace (`krizaka/krizaka-platform-kit`), the workspace contract
> ([`krizaka/orazaka/AGENTS.md`](https://github.com/krizaka/orazaka/blob/main/AGENTS.md)) applies as well.

## What belongs here — and what does not

A type belongs in the kit when it carries an **invariant with several authors**: the same rule, written by several
services, where one copy drifting is a defect. It does not belong here when the copies differ for a reason — each
context's datasource, queues and outbox schema are its own (ADR-058 in the Orazaka workspace; superseded in part by
ADR-073, which created this kit).

- **No product vocabulary.** No `orazaka`, `orochia`, exchange names, queue names, routing keys or table names other
  than documented defaults. The kit is configured by `krizaka.*` properties.
- **Declared, never inferred.** A behaviour that changes a guarantee (which dedup store, whether the relay runs) is a
  property the application sets; the kit never picks one from what happens to be on the classpath, and a missing
  prerequisite fails at startup with the reason.
- **Optional dependencies stay optional.** Spring Security, Spring MVC, JDBC and RabbitMQ are `optional`; every
  auto-configuration is guarded so that an application without them starts. A test with `FilteredClassLoader` proves it for each one.
- **Public API is documented.** Every public type and method has javadoc; `./mvnw verify -Prelease -Dgpg.skip` lints it.

## Definition of done

1. `./mvnw verify -Prelease -Dgpg.skip` is green.
2. A new behaviour has a test of the invariant it carries (not only of the happy path), and its auto-configuration has
   an `ApplicationContextRunner` test of every condition.
3. Inside the Orazaka workspace, `./mvnw install` from the root is green: every consumer still builds and its governance
   rules (`KIT-001`…`KIT-004`) pass.
4. [README.md](README.md) and [CHANGELOG.md](CHANGELOG.md) describe the change.
