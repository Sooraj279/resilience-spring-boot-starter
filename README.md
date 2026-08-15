# resilience-starter

[![build](https://github.com/Sooraj279/resilience-spring-boot-starter/actions/workflows/build.yml/badge.svg)](https://github.com/Sooraj279/resilience-spring-boot-starter/actions)

Rate limiting and circuit breaking for Spring Boot 4, with pluggable algorithms and
optional Redis-backed distributed coordination.

Add one dependency, annotate a method, and get per-key throttling with correct HTTP
semantics — `429` with `Retry-After`, `X-RateLimit-*` headers, RFC 9457 problem details,
and Micrometer counters — without writing any of that yourself.

```java
@GetMapping("/api/reports")
@RateLimited(key = "#userId", capacity = 100, refillPeriodSeconds = 60)
public Report generate(@RequestParam String userId) {
    return service.buildExpensiveReport(userId);
}
```

---

## Why

Two failure modes account for a large share of production incidents, and both have the
same shape: something upstream or downstream behaves worse than you assumed, and your
service has no opinion about it.

**Protecting the API from its callers.** A single client — a runaway retry loop, a scraper,
a well-meaning batch job — can consume capacity intended for everyone. The fix is not
"add more servers"; it is to make the cost of misbehaviour fall on the misbehaving caller.
That requires per-key accounting, not a global cap, and it requires telling the caller
*when* to come back rather than just refusing.

**Protecting yourself from a failing dependency.** When a downstream service starts timing
out, the naive response — keep calling it — is actively harmful. Threads pile up waiting on
a service that will not answer, and a failure in one dependency becomes an outage in your
own. The fix is to stop calling it for a while, fail fast, and probe occasionally to see
whether it has recovered.

Both problems are solved. Resilience4j and Bucket4j are mature and you should probably use
them. This library exists because implementing the algorithms yourself is the only way to
understand the tradeoffs inside them — and because the Redis coordination path, the
fail-open policy, and the Spring Boot 4 auto-configuration are worth getting right in the
open where they can be read.

---

## Quick start

Available through [JitPack](https://jitpack.io).

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.Sooraj279.resilience-spring-boot-starter</groupId>
    <artifactId>resilience-starter</artifactId>
    <version>v0.1.0</version>
</dependency>
```

That is the whole setup. Auto-configuration registers everything; there is no
`@EnableResilience`.

### Rate limiting

```java
@RestController
public class ReportController {

    @GetMapping("/api/reports")
    @RateLimited(key = "#userId", capacity = 100, refillPeriodSeconds = 60)
    public Report generate(@RequestParam String userId) { ... }
}
```

The `key` is a SpEL expression evaluated against the method's parameters, so limits are
per user, per API key, per tenant — whatever you can name. Exceed it and the caller gets:

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
Retry-After: 47

{"type":"about:blank","title":"Too Many Requests","status":429,
 "detail":"Rate limit exceeded for ReportController#generate"}
```

### Circuit breaking

```java
@GetMapping("/api/inventory")
@CircuitBroken(failureThreshold = 5, openTimeoutMillis = 10_000)
public Inventory lookup(String sku) {
    return flakyDownstreamClient.get(sku);   // throws when the dependency is unhealthy
}
```

After five consecutive failures the circuit opens and further calls return `503` immediately
without touching the dependency. Ten seconds later exactly one trial call is admitted; if it
succeeds the circuit closes, if it fails the timer restarts.

### Going distributed

In-memory limiters count per JVM. Run four instances behind a load balancer and a "100 per
minute" limit quietly becomes 400 per minute. Switch the backend and the count moves to Redis:

```yaml
resilience:
  rate-limit:
    backend: redis
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

No code changes. The annotations are identical.

---

## Architecture

```
resilience-core            algorithms, zero Spring dependencies
      │
      ├──> resilience-autoconfigure    annotations, aspects, wiring, metrics, HTTP mapping
      │            │
      │            └──> resilience-redis    Lua-backed distributed limiters
      │                        │
      └────────────────────────┴──> resilience-starter    what consumers depend on
```

| Module | Contains |
|---|---|
| `resilience-core` | `TokenBucketRateLimiter`, `SlidingWindowRateLimiter`, `CircuitBreaker`, and the `RateLimiter` / `RateLimiterFactory` contracts. Plain Java. |
| `resilience-autoconfigure` | `@RateLimited`, `@CircuitBroken`, the AOP aspects, `ResilienceAutoConfiguration`, Micrometer counters, and the `@RestControllerAdvice` that turns exceptions into problem details. |
| `resilience-redis` | `RedisTokenBucketRateLimiter`, `RedisSlidingWindowRateLimiter`, and the two Lua scripts they execute. Activated by a property. |
| `resilience-starter` | Dependency aggregator. The only artifact consumers name. |
| `demo-app` | Runnable example exercising both annotations. |
| `resilience-benchmarks` | JMH harness for the in-memory limiters. |

---

## Algorithms

|  | Token bucket | Sliding window log |
|---|---|---|
| Memory per key | 2 fields, constant | 1 timestamp per request in window |
| Bursts | Allowed up to capacity | Strictly enforced |
| Accuracy | Approximate at window edges | Exact |
| Redis structure | `HASH` (tokens, last_refill) | `ZSET` of timestamps |
| Cost of a rejection | O(1) | O(1) amortised, O(n) eviction |
| Best for | General API throttling | Hard contractual limits |

**Token bucket** holds a balance that refills continuously — `refillTokens` per
`refillPeriodSeconds`, computed in nanoseconds so refill is smooth rather than stepped. A
client that has been idle accumulates up to `capacity` and can spend it in a burst. This is
usually what you want: it absorbs legitimate spikes without punishing them.

**Sliding window log** records the timestamp of every request and counts those still inside
the window. Nothing is approximated, so a "100 per minute" guarantee holds at every instant,
including across the boundary where a fixed-window counter would let 200 through. The cost
is memory proportional to the request rate.

Select per method:

```java
@RateLimited(key = "#tenantId", capacity = 1000, refillPeriodSeconds = 3600,
             algorithm = Algorithm.SLIDING_WINDOW)
```

---

## Requirements

- **Spring Boot 4.1+**
- **Java 21+**
- **Redis 6+** — only for `backend: redis`

The build targets a single Boot generation deliberately. Boot 4 split the monolithic
`spring-boot-autoconfigure` into per-technology modules and renamed the starters
(`spring-boot-starter-web` → `spring-boot-starter-webmvc`, `spring-boot-starter-aop` →
`spring-boot-starter-aspectj`, and so on). A single artifact that auto-configures cleanly
against both Boot 3 and Boot 4 would need reflection or duplicated conditions for every
touchpoint. Supporting one generation properly beats supporting two badly.

---

## Configuration

| Property | Default | Description |
|---|---|---|
| `resilience.enabled` | `true` | Master switch. `false` backs the entire auto-configuration off — no aspects, no beans, no proxying overhead. |
| `resilience.rate-limit.backend` | `in-memory` | `in-memory` or `redis`. Selects which `RateLimiterFactory` is registered. |
| `resilience.rate-limit.fail-open` | `true` | Behaviour when Redis is unreachable. `true` allows the request, `false` rejects it. Only meaningful with `backend: redis`. |

Per-method settings live on the annotations:

| `@RateLimited` | Default | |
|---|---|---|
| `key` | *required* | SpEL over method parameters, e.g. `"#userId"` |
| `capacity` | `100` | Bucket size, or max requests per window |
| `refillTokens` | `100` | Tokens added per period. Ignored by `SLIDING_WINDOW`. |
| `refillPeriodSeconds` | `60` | Refill period, or window length |
| `algorithm` | `TOKEN_BUCKET` | `TOKEN_BUCKET` or `SLIDING_WINDOW` |

| `@CircuitBroken` | Default | |
|---|---|---|
| `failureThreshold` | `5` | Consecutive failures before opening |
| `openTimeoutMillis` | `10000` | Cooldown before a trial call is admitted |

### Observability

Three Micrometer counters, each tagged `name` with the limiter or circuit identifier
(`ClassName#methodName`):

```
resilience.ratelimit.allowed
resilience.ratelimit.rejected
resilience.circuit.rejected
```

They register lazily on first increment, so they will not appear in `/actuator/metrics`
until traffic has flowed. Response headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`,
`Retry-After`) are written automatically on servlet requests and skipped elsewhere.

### Redis keys

```
ratelimit:tb:<limiterName>:<key>     HASH,  TTL = refillPeriodSeconds * 2
ratelimit:sw:<limiterName>:<key>     ZSET,  TTL = window length
```

---

## Design decisions

### `RateLimiter` returns a result object, not a boolean

`tryAcquire` returns `RateLimitResult(allowed, remaining, retryAfterMillis)`.

A boolean is enough to decide whether to proceed and nothing else. But the caller has to
populate `X-RateLimit-Remaining` and `Retry-After`, and both are known inside the limiter at
the moment of the decision — the token bucket already computed how far below one token the
balance sits; the sliding window already knows the oldest timestamp. Returning a boolean
throws that away and forces either a second call (racy, and wrong under concurrency) or a
second data structure to recover it.

`remaining = -1` is the explicit "unknown" case, used when the backend is degraded. The
aspect omits the header rather than emitting a misleading number.

### `compareAndSet` guards the `HALF_OPEN` transition

```java
return coolDownElapsed && state.compareAndSet(OPEN, HALF_OPEN);
```

When the cooldown expires, the circuit must admit exactly one probe. A plain
`if (elapsed) { state = HALF_OPEN; return true; }` lets every thread that arrives in the same
instant read `OPEN`, see the elapsed timer, and proceed — sending a thundering herd at a
dependency that has just started breathing again, which is precisely how a recovering service
gets knocked back down.

`compareAndSet` makes the transition a single atomic claim: one thread wins, everyone else
sees `HALF_OPEN` and is refused. The trial is genuinely a trial.

### The Redis path fails open by default

If Redis is unreachable, `tryAcquire` logs a warning and returns `allow(-1)`.

The reasoning: a rate limiter is a protective mechanism, not a business requirement. If the
protection becomes unavailable, degrading to "unprotected" is bad; degrading to "the entire
API returns 503" is worse. An outage in an auxiliary component should not become an outage in
the thing it was auxiliary to.

Set `fail-open: false` when the limit is the requirement rather than a safeguard — metered
billing, a contractual quota, or abuse protection where letting traffic through is more
expensive than turning users away. That is a real choice with real consequences in both
directions, which is why it is a property and not a hardcoded policy.

One consequence worth knowing: with `fail-open: true`, a dead Redis is indistinguishable from
a healthy one by looking at response codes — everything succeeds. The signal is the absence
of the `X-RateLimit-Remaining` header, plus the warning in the logs. `smoke-test.ps1` asserts
on that header for exactly this reason.

### The token bucket math lives in Lua, not Java

Reading `tokens` and `last_refill`, computing the refill, and writing the new balance is a
read-modify-write cycle. Split across a network round trip, two instances interleave and both
observe the same balance, both decide there is a token available, and the bucket goes
negative — the limit silently stops being a limit under exactly the concurrency it exists to
handle.

Redis executes a Lua script atomically: no other command runs between its first and last
statement. Moving the arithmetic into the script makes each `tryAcquire` a single atomic
operation, and collapses three round trips into one. `WATCH`/`MULTI` could achieve
correctness, but with retry loops under contention rather than a single deterministic call.

### `resilience-core` has zero Spring dependencies

The algorithms are testable with plain JUnit and no container — the core test suite runs in
well under a second, which matters because concurrency bugs are found by running tests many
times, not once. It also keeps the module usable outside Spring entirely, and forces the
boundary to stay honest: anything Spring-shaped that leaks into `core` is a design smell that
shows up immediately as a new dependency.

### Optional-dependency beans live in nested conditional configurations

Micrometer is `optional` and Spring Web is `provided` in `resilience-autoconfigure`. Neither
is transitive, so neither reaches a consumer that does not ask for them — including this
project's own `resilience-redis` module.

Spring reflects over the auto-configuration class as a whole (to deduce a bean type for a bare
`@ConditionalOnMissingBean`, for instance), and `Class.getDeclaredMethods()` resolves the
parameter and return types of *every* declared method at once. One method mentioning an absent
type therefore fails the entire auto-configuration with a `NoClassDefFoundError` — and a
method-level `@ConditionalOnClass` does not save it, because the condition is never reached.

So no method declared directly on `ResilienceAutoConfiguration` names an optional type. The
Micrometer, servlet, and Spring Web beans live in nested `@Configuration` classes guarded by
`@ConditionalOnClass(name = ...)` — declared by name so that evaluating the condition never
triggers class loading. `ResilienceAutoConfigurationTest` pins this with `FilteredClassLoader`
runs that reproduce a downstream classpath.

### The auto-configuration is ordered after Micrometer's

```java
@AutoConfiguration(afterName = {
    "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
    "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration"
})
```

`@ConditionalOnBean(MeterRegistry.class)` only sees bean definitions registered by
auto-configurations processed earlier. Boot sorts auto-configurations by class name before
applying ordering annotations, and `io.github.sooraj279...` sorts ahead of
`org.springframework.boot...` — so without this, the condition evaluates before any
`MeterRegistry` exists, silently fails, and no metrics are ever recorded. Nothing throws. The
counters simply never appear.

---

## Benchmarks

JMH, 3 forks × (5 warmup + 10 measurement iterations × 10s), 8 threads, throughput mode.
Both limiters configured with a capacity high enough that no request is rejected, so the
numbers measure bookkeeping cost rather than rejection cost.

| Benchmark | Throughput | Error (99.9% CI) |
|---|---:|---:|
| `slidingWindow` | 6,805,388 ops/s | ± 303,824 |
| `tokenBucket` | 6,165,360 ops/s | ± 781,900 |

Read this as *both limiters cost well under a microsecond per call and neither is a
bottleneck*, not as "sliding window is faster." The confidence intervals overlap; the
honest conclusion is that they are equivalent at this scale, and the choice between them
should be made on the memory and burst-semantics tradeoffs in the table above, not on speed.

Measured on a developer laptop, JDK 25, 8 threads — absolute numbers will differ on your
hardware. Reproduce with:

```bash
./mvnw package -pl resilience-benchmarks -am
java -jar resilience-benchmarks/target/benchmarks.jar -f 3 -wi 5 -i 10
```

The Redis-backed limiters are deliberately not benchmarked here: the result would be a
measurement of network latency to your Redis, which tells you nothing portable.

---

## Limitations

Honest list of what this does not do, and what it would take to fix.

**Circuit breaker state is per instance.** `CircuitBreakerRegistry` holds breakers in a
`ConcurrentHashMap`, so each JVM decides independently. With N instances, a failing dependency
absorbs up to N × `failureThreshold` failures cluster-wide before every instance has opened,
and each opens on its own schedule. *Fix:* move the state to Redis as the rate limiters
already are — a small Lua script over a hash of `state`, `consecutive_failures`, `opened_at`.
The `compareAndSet` guard becomes the script's atomicity. Not done yet because a distributed
breaker also wants shared half-open election, which is a larger design than it first appears.

**No sliding-window-counter approximation.** The sliding window is a true log: one timestamp
per request, per key, for the window's duration. At 1,000 req/s over a 60s window that is
60,000 longs for a single key. The standard mitigation is the weighted two-bucket counter,
which is approximate but O(1) in memory. *Fix:* a third `Algorithm` value; the `RateLimiter`
contract already accommodates it without changes.

**No per-tier priorities or weighted costs.** Every call costs exactly one token. A real API
usually wants a cheap read and an expensive report to draw differently, and a paid tier to
draw from a larger bucket than a free one. *Fix:* a `cost` attribute on `@RateLimited` (the
Lua scripts already take `requested` as a parameter and would need no change), plus resolving
`capacity` from a caller attribute rather than a compile-time constant — which means the
annotation values need to become SpEL, like `key` already is.

**Servlet only.** `ServletRateLimitHeaderWriter` is the sole `RateLimitHeaderWriter`
implementation. WebFlux applications get rate limiting and the correct status codes, but no
`X-RateLimit-*` headers. *Fix:* a reactive implementation writing to `ServerWebExchange`; the
interface was extracted with exactly this in mind and deliberately references no servlet types.

**SpEL keys need `-parameters`.** Key resolution reads parameter names reflectively. Spring
Boot's parent enables `-parameters` by default, so this works out of the box — but a consumer
with a hand-rolled compiler configuration will silently get `"default"` as the key for every
caller, collapsing all users into one bucket. Worth an explicit failure rather than a quiet
fallback.

**Idle Redis buckets reset.** The token bucket key carries a TTL of `refillPeriodSeconds * 2`.
A key idle past that expires, and the next request starts from a full bucket. Harmless for
throttling — an idle client would have refilled to capacity anyway — but it means the Redis
state is not a durable audit trail of usage.

**No introspection endpoint.** `CircuitBreakerRegistry.snapshot()` exists and returns live
breaker state, but nothing exposes it. There is no way to ask a running application which
circuits are currently open. *Fix:* an actuator endpoint; the snapshot method was written for
it.

---

## Building and testing

```bash
./mvnw verify          # unit tests + Testcontainers integration test
```

Integration tests need Docker. The Redis integration test starts a real `redis:7` container
via Testcontainers rather than mocking `StringRedisTemplate`, because the thing under test is
the Lua script's behaviour inside Redis — a mock would assert that the code calls the mock.

For runtime behaviour, `smoke-test.ps1` exercises a running `demo-app` end to end: both
algorithms, per-key isolation, the circuit breaker's trip and half-open recovery, response
headers, and metrics registration.

```powershell
docker run --rm -p 6379:6379 redis:7      # terminal 1
./mvnw -pl demo-app spring-boot:run       # terminal 2
./smoke-test.ps1                          # terminal 3
```

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
