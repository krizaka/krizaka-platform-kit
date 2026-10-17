# krizaka-app-config

App version compatibility between a backend and the apps (mobile, desktop) that call it. Written once; the TypeScript
twin for Next.js backends and the clients is [`@krizaka/app-config`](https://www.npmjs.com/package/@krizaka/app-config).

```xml
<dependency>
  <groupId>com.krizaka</groupId>
  <artifactId>krizaka-spring-boot-starter-app-config</artifactId>
</dependency>
```

## The contract

| What | Rule |
|:---|:---|
| Headers an app sends | `X-Client-Platform` (`ios` `android` `macos` `windows` `linux` `web`), `X-Client-Version` (`1.4.0`), `X-Contract-Version` (integer). A request without them is not an app and passes. |
| `GET /api/v1/app-config` | Public, `?platform=` (or the header) `&version=` `&build=` `&locale=`. `Cache-Control: public, max-age=60, stale-while-revalidate=300`, `ETag`, `Vary: Accept-Language`. Body: [`contract/app-config.schema.json`](src/main/resources/contract/app-config.schema.json). |
| `updateMode` | `force` below `minSupportedVersion`, `soft` below `latestVersion`, otherwise `none`; the operator's `updateOverride` wins (`force` for everyone is the kill switch). No version sent: the override or `none`. |
| `426 Upgrade Required` | Version below the minimum, or a contract below `contractMin`. `application/problem+json` with `code` `upgrade-required` / `contract-unsupported`, an `Upgrade: krizaka-app/<min>` header and the `appConfig` payload (`updateMode: force`). Runs before authentication, so an old client with an expired token still learns it must update. |
| N-1 contracts | A contract in `[contractMin, contractMax)` is served with `Deprecation: true` and, when `contractSunset` is announced, `Sunset: <HTTP date>`. A client newer than the backend passes. |
| Fail open | The parameters are cached (`cache-ttl`, 30 s); when the store fails the last value is served, and with none the permissive defaults. An outage of the parameters never becomes an outage of the app. |

One fixture, two implementations: [`contract/cases.json`](src/test/resources/contract/cases.json) is evaluated by this
module and by `@krizaka/app-config`; both must agree on every case.

## Wiring

1. Create the tables: apply [`krizaka-app-config-schema.sql`](src/main/resources/krizaka-app-config-schema.sql) with the
   service's own migration (it seeds version 1 for every platform: no minimum, no maintenance, contract 1).
2. Let the public path through the security chain: `auth.requestMatchers("/api/v1/app-config").permitAll()`.
3. Optional admin API: `krizaka.app-config.admin.enabled=true` (authority `krizaka.app-config.admin.authority`, default
   `ROLE_ADMIN`; it fails closed without Spring Security). `GET /api/v1/admin/app-config[/{platform}[/history]]`,
   `PUT /api/v1/admin/app-config/{platform}` (body = the parameters + `reason`), `POST …/{platform}/rollback/{revision}`
   (`{"reason": "…"}`). Every publication is a new revision with who, when and why; a rollback is a new revision too.

| Property | Default | |
|:---|:---|:---|
| `krizaka.app-config.enabled` | `true` | Whole module |
| `krizaka.app-config.store` | `jdbc` | `memory` for tests; `jdbc` needs a `JdbcTemplate` and fails at startup without one |
| `krizaka.app-config.cache-ttl` | `30s` | Per instance |
| `krizaka.app-config.http-max-age` | `60s` | The public endpoint's `max-age` |
| `krizaka.app-config.guard.enabled` / `include` / `exclude` | `true` / `/api/**` / the endpoint and the admin API | The 426 filter |

The values that decide what an app does (minimum, latest, maintenance, flags, texts) are **database parameters**, never
properties; they are validated by safety bounds (minimum ≤ latest, contract range not empty, `https` store URL).
