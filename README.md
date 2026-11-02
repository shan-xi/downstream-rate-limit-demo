# Downstream Rate Limit Demo - Redis + Redisson

Spring Boot demo showing how to protect downstream systems A/B/C when the application runs on multiple instances.

## Architecture

```text
                    Load Balancer
                         |
              +----------+----------+
              |                     |
          App Instance #1       App Instance #2
              |                     |
              +----------+----------+
                         |
                    Redis + Redisson
                         |
             +-----------+-----------+
             |           |           |
          A: 5 RPS    B: 20 RPS    C: 2 RPS
             |           |           |
          Queue A     Queue B     Queue C
             |           |           |
          Workers     Workers     Workers
             |           |           |
             A           B           C
```

## Important change from v4

The previous version implemented the distributed token bucket directly with a Redis Lua script.
This version uses **Redisson's `RRateLimiter`**, so the application code does not contain custom Lua.

The limiter uses `RateType.OVERALL`, which means the configured rate is shared globally across all application
instances.
For example, if A is configured for 5 RPS, three application instances still share approximately 5 RPS total rather than
getting 5 RPS each.

## APIs

### Async

```bash
curl -i -X POST http://localhost:8080/requests/async \
  -H 'Content-Type: application/json' \
  -d '{"downstream":"A","payload":"hello"}'
```

Returns `202 Accepted` when the request is accepted into the in-memory bounded queue.
Returns `429 Too Many Requests` when the distributed downstream rate limit or queue capacity is exhausted.

### Sync

```bash
curl -i -X POST http://localhost:8080/requests/sync \
  -H 'Content-Type: application/json' \
  -d '{"downstream":"A","payload":"hello"}'
```

The sync endpoint does not wait for a rate-limit token. If the global downstream quota is unavailable, it immediately
returns `429`.

## Configuration

```yaml
downstream:
  routes:
    A:
      rps: 5
      queue-capacity: 100
      workers: 2
      latency-ms: 80
    B:
      rps: 20
      queue-capacity: 200
      workers: 4
      latency-ms: 50
    C:
      rps: 2
      queue-capacity: 50
      workers: 1
      latency-ms: 120
```

## Redis

Start Redis:

```bash
docker compose up -d redis
```

Then start the Spring Boot application:

```bash
./mvnw spring-boot:run
```

If you do not have the Maven wrapper in the project, use your installed Maven command instead.

## Why Redisson?

The application needs one shared rate-limit state across multiple instances. Redisson provides a distributed rate
limiter abstraction backed by Redis, so the application does not need to maintain its own Lua token-bucket script.

The important design decision is still the same:

> The downstream system is the protected resource, so A/B/C each get an independent global rate limit.

## Production extensions

For a real production system, consider:

- Durable queue such as Kafka/SQS/RabbitMQ instead of the demo's in-memory queue.
- Retry with exponential backoff and jitter.
- Dead-letter queue for messages that cannot be delivered.
- Idempotency when downstream POSTs may be retried.
- HTTP connection/read timeouts.
- Circuit breaker when a downstream is unhealthy.
- Concurrency limits in addition to RPS limits.
- Metrics for accepted/rejected requests, queue depth, downstream latency, and downstream errors.
- Redis HA/managed Redis for production availability.

## Worker executor lifecycle

`DownstreamRouter` owns one `ExecutorService` per downstream. Spring does not automatically manage an executor created
with `Executors.newFixedThreadPool(...)`, so the router keeps the executor references and exposes a `@PreDestroy`
method.

When the Spring application context is closed:

1. Spring invokes `DownstreamRouter.shutdown()`.
2. The router calls `shutdownNow()` on each executor.
3. `shutdownNow()` interrupts the worker threads.
4. A worker blocked in `queue.take()` receives `InterruptedException`.
5. `workerLoop()` restores the interrupt flag and exits.
6. The router waits up to 5 seconds for termination.

We intentionally use `shutdownNow()` here rather than only `shutdown()`. A worker can be blocked forever in
`BlockingQueue.take()` when the queue is empty; `shutdown()` does not interrupt an already-running worker task, so it
would not reliably stop these long-lived worker loops.

For a production system, decide explicitly whether shutdown should **drain queued work** or **stop immediately**. This
demo chooses immediate shutdown for clarity. A durable Kafka/SQS/RabbitMQ queue should be used if accepted messages must
survive application termination.

### Rate limiting placement

The important flow is:

```text
SYNC:  API -> Redis/Redisson rate limiter -> downstream

ASYNC: API -> bounded queue -> worker -> Redis/Redisson rate limiter -> downstream
```

For async traffic, the rate limiter is deliberately placed in the worker. If a request waits in
the queue for a while, it does not consume a rate-limit permit prematurely. The permit is consumed
when the worker is ready to make the actual outbound call. Because Redisson uses `RateType.OVERALL`,
the configured RPS is shared across all application instances.

The async endpoint therefore returns `202 Accepted` when the bounded queue has capacity. If the
queue is full, it returns `429`. The worker waits for a permit by polling every 50 ms; in production,
a delayed queue or a blocking rate-limiter API could be used to avoid polling.
