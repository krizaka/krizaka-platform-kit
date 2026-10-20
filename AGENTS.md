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

## Tests

- **No test, not done.** Every feature and every fix ships with its tests in the same pull request: a unit test for
  logic, a Testcontainers integration test for an adapter, an endpoint or a flow.
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
  The **import layout is stated, not detected**: `rewrite.yml` declares the google-java-format layout (the one Spotless
  applies) as style `com.krizaka.style.GoogleJavaImportLayout`, activated in the root `pom.xml`. OpenRewrite's autodetected
  layout depended on the runner (same commit green on one GitHub runner image, `OrderImports` on 21 files red on the next:
  issue #22) — never remove it to silence a red check.
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
- A rule never passes over nothing (no `allowEmptyShould`, Orazaka GOV-006): a module applies the rules whose subject it has (a library without controllers does not apply the controller naming rule).
- A rule is never weakened, skipped or deleted to get a PR green (AGENTS §5.3 of the org); fix the code or freeze the existing debt.

## Definition of done

1. `./mvnw verify -Prelease -Dgpg.skip` is green (tests, governance, javadoc).
2. Inside the Orazaka workspace, `./mvnw install` from the root is green.
3. [README.md](README.md) and [CHANGELOG.md](CHANGELOG.md) describe the change.
