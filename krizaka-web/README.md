# krizaka-web

**The HTTP baseline of a Spring MVC service, written once:** one error format (RFC 9457 Problem Details with a stable
`code`), `X-Request-Id` correlation into the logs, the same Jackson 3 defaults everywhere, opaque cursor pagination and
CORS for declared origins only.

```xml
<dependency>
    <groupId>com.krizaka</groupId>
    <artifactId>krizaka-web</artifactId>
</dependency>
```

Spring MVC (`spring-boot-starter-webmvc`) and bean validation (`spring-boot-starter-validation`) are **optional**
dependencies: bring them yourself. Without Spring MVC, or outside a servlet application, the auto-configuration stays
out of the way; `CorrelationId` and `Cursor` remain usable from a worker.

## The error contract

Every error a service answers is `application/problem+json`:

```json
{
  "type": "https://krizaka.com/problems/auction-closed",
  "title": "auction closed",
  "status": 409,
  "detail": "The auction closed at 18:00.",
  "code": "auction-closed",
  "requestId": "0b6c3f1e-6a52-4c4e-9a43-61f0f1f2a1d7"
}
```

| Field | Meaning |
|:---|:---|
| `type` | `krizaka.web.problems.base-type` + `code` — where the code is documented |
| `title` | the code, words separated by spaces |
| `status` | the HTTP status |
| `detail` | the exception's message — shown to the caller, so written for the caller. **Absent on a 500.** |
| `code` | the stable slug a client branches on; never changes once published |
| `requestId` | the request's `X-Request-Id`, also on every log line of the request |

A bean validation failure (`@Valid` body, constrained `@RequestParam`) is a **422** with `code: validation-failed` and
one entry per violation:

```json
{
  "type": "https://krizaka.com/problems/validation-failed",
  "title": "validation failed",
  "status": 422,
  "detail": "The request has invalid fields.",
  "code": "validation-failed",
  "requestId": "…",
  "errors": [
    { "field": "name", "code": "NotBlank", "message": "must not be blank" },
    { "field": "quantity", "code": "Positive", "message": "must be greater than 0" }
  ]
}
```

What else happens:

- Spring MVC's own failures (unreadable body, `405`, `415`…) keep Spring's status and body, with `requestId` added.
- An exception annotated with `@ResponseStatus` keeps its status and its `reason`.
- Spring Security's `AccessDeniedException` / `AuthenticationException` are **left to Spring Security** (`403`/`401`):
  catching them would turn a refusal into a `500`.
- Anything else is a **500** with `code: internal` and **no `detail`**: the exception is logged with its `requestId`,
  never sent.

### Exceptions

Raise a `DomainException` — the only exception that leaves a service as a 4xx. Its status is always 4xx and its code a
lowercase slug (`[a-z0-9]+(-[a-z0-9]+)*`); anything else fails when the exception is created.

| Exception | Status | Default code |
|:---|:---|:---|
| `NotFoundException` | 404 | `not-found` |
| `ConflictException` | 409 | `conflict` |
| `ForbiddenException` | 403 | `forbidden` |
| `ValidationException` | 422 | `validation-failed` |
| `InvalidCursorException` | 400 | `invalid-cursor` |

```java
throw new ConflictException("auction-closed", "The auction closed at 18:00.");
```

Each concrete exception takes a more precise code; extend `DomainException` for another 4xx.

## Request correlation

`CorrelationIdFilter` runs first in the filter chain. It keeps the caller's `X-Request-Id` when it is 1–128 characters
of `[A-Za-z0-9._:@/+=-]` (anything else could forge a log line, and is replaced), creates a UUID otherwise, sends it
back in the response, and puts it in the MDC as `requestId` for the whole request — error dispatch included.

Outside a request (a consumer, a scheduled job), open a scope yourself:

```java
try (CorrelationId.Scope scope = CorrelationId.open(message.getMessageProperties().getCorrelationId())) {
  handle(message);
}
```

`CorrelationId.current()` reads it (or `null`), `CorrelationId.currentOrNew()` returns it or a new UUID — for a producer
that must stamp an id on what it sends.

## JSON defaults

`KrizakaJsonDefaults` (Jackson 3): ISO-8601 dates and durations, `null` fields omitted, unknown fields ignored — the
tolerant reader that lets a producer add a field without breaking its consumers. It runs before Spring Boot's own
customizer, so any `spring.jackson.*` property you set still wins.

## Cursor pagination

Keyset pagination — `WHERE id > :after ORDER BY id LIMIT :limit + 1` — with an opaque base64url token:

```java
@GetMapping
CursorPage<Item> list(@RequestParam(required = false) String cursor) {
  Cursor page = Cursor.fromRequest(cursor, 20);
  List<Item> rows = items.after(page.afterAsLong(), page.limit() + 1); // one more than the page
  return CursorPage.of(rows, page, Item::id);
}
```

```json
{ "items": [ … ], "nextCursor": "MXwyMHw0Mg" }
```

The extra row proves there is a next page without a `COUNT`, and is not served. `nextCursor` is absent on the last
page. The page size is capped at 100. `Cursor.decode` accepts only what `encode` produced — any other token is
`400 invalid-cursor`.

## CORS

Nothing is open unless you declare it:

```yaml
krizaka:
  web:
    cors:
      allowed-origins: https://app.example.com,https://admin.example.com
```

With at least one origin, the module registers a `corsConfigurationSource` bean (the name Spring Security's `cors()`
looks up) **and** the same policy with Spring MVC, so preflights are answered the same with or without Spring Security:
exact origins, `GET POST PUT PATCH DELETE OPTIONS`, `Authorization` / `Content-Type` / `X-Request-Id` request headers,
`X-Request-Id` exposed, no credentials. A wildcard origin is refused at startup.

## Properties

| Property | Default | |
|:---|:---|:---|
| `krizaka.web.problems.base-type` | `https://krizaka.com/problems/` | absolute, ending with `/` |
| `krizaka.web.cors.allowed-origins` | *(none)* | exact origins; `*` refused |

The module also sets `spring.mvc.problemdetails.enabled=true` as the **lowest-priority** property source: your
configuration can still turn it off.

## Replacing a piece

Every bean backs off when you declare your own: a `ResponseEntityExceptionHandler` of yours replaces
`ProblemDetailsAdvice`; a bean named `krizakaCorrelationIdFilter`, `krizakaJsonDefaults` or `corsConfigurationSource`
replaces the corresponding one. `@WebMvcTest` slices get the same beans as the application.
