# krizaka-platform-kit — Scope (agent-neutral)

> Cross-cutting building blocks for Spring Boot services, published on Maven Central as `com.krizaka:krizaka-security`,
> `com.krizaka:krizaka-messaging`, `com.krizaka:krizaka-web`, `com.krizaka:krizaka-observability` and the four
> `krizaka-spring-boot-starter-*`. Orazaka consumes them; so can any application. When
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
- **A starter is a POM; the code is in the module.** `starters/krizaka-spring-boot-starter-*` hold dependencies and
  one integration test that starts an application on that starter alone — never a class. Behaviour goes in the module
  (`krizaka-web`, `krizaka-security`, …), which stays usable without its starter.
- **Releases are release-please's.** Pull request titles are Conventional Commits; nobody edits a version or the
  generated part of the CHANGELOG by hand. The kit has its own SemVer version; its `krizaka-parent` and
  `krizaka-build.version` move together, to a `krizaka-build` release, before a kit release.
- **Public API is documented.** Every public type and method has javadoc; `./mvnw verify -Prelease -Dgpg.skip` lints it.

## Tests

- **No test, not done.** Every feature and every fix ships with its tests in the same pull request: a unit test for the
  invariant it carries, an `ApplicationContextRunner` test for every auto-configuration condition, an integration test
  for a starter (above).
- **A fix starts with a failing test** that reproduces the bug; the fix turns it green and the test stays as the guard.
- CI runs them on every pull request and must be green. A test is never skipped, `@Disabled`, weakened or
  deleted to get there.

## Static rules & codemods (OpenRewrite, ArchUnit)

The org code rules (addendum 2026-10-15) are checked by tools, not by review.

- **OpenRewrite** (`rewrite-maven-plugin`, configured once in `krizaka-parent`, inherited by every JVM repository):
  common static analysis, test best practices, Java 21 idioms, Spring idioms (no `@Autowired` on a constructor; `@Bean` methods stay public), no duplicate POM dependency. `./mvnw rewrite:dryRun` lists what would change and **fails** if
  anything would (CI runs it: `rewrite: true` in `.github/workflows/ci.yml`); `./mvnw rewrite:run` applies it — behaviour
  identical, run the tests, land it in a small pull request. A recipe that is wrong for this repository is excluded here
  (`<exclusions>` / a `rewrite.yml`), with the reason; test fixtures (`**/fixture/**`) are excluded everywhere.
- **ArchUnit** (shared rules in `krizaka-test-support`: `CodeRules`, `ConfigBindingRules`, `SourceRules`,
  `ArchitectureRules`): the governance tests of a module call them. `ArchitectureRules` holds the structure — layers
  (web → service → repository, none skipped, none calling up), no package cycle, `*Controller` / `*Service` /
  `*Repository` names, `@Value` and the environment read only in configuration, `@Transactional` only on services. The
  older `CodeRules` hold the rest (no field injection, no `System.out`, domain free of framework, one class per file…).
- **Debt only shrinks — `FrozenRules`** (ArchUnit `FreezingArchRule`): a rule a module cannot satisfy yet is checked with
  `FrozenRules.check(ArchitectureRules.xxxRule(...), CLASSES)`. The violations that exist are recorded in the module's
  `archunit_store/` (committed); a **new** violation fails, a fixed one leaves the store (commit it). A rule with no store
  fails: record a new rule's debt once with `-Darchunit.freeze.store.default.allowStoreCreation=true`, review the store,
  commit it. Never add a violation to a store by hand to get a build green.
- A rule is never weakened, skipped or deleted to get a PR green (AGENTS §5.3 of the org); fix the code or freeze the existing debt.

## Definition of done

1. `./mvnw verify -Prelease -Dgpg.skip` is green.
2. A new behaviour has a test of the invariant it carries (not only of the happy path), and its auto-configuration has
   an `ApplicationContextRunner` test of every condition.
3. Inside the Orazaka workspace, `./mvnw install` from the root is green: every consumer still builds and its governance
   rules (`KIT-001`…`KIT-004`) pass.
4. [README.md](README.md) and [CHANGELOG.md](CHANGELOG.md) describe the change.
