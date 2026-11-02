package com.example.ratelimit.controller;

import com.example.ratelimit.model.AsyncRequest;
import com.example.ratelimit.model.Downstream;
import com.example.ratelimit.model.RequestPayload;
import com.example.ratelimit.service.DistributedRateLimiter;
import com.example.ratelimit.service.DownstreamRouter;
import com.example.ratelimit.service.SyncDownstreamClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/requests")
public class RequestController {
    private final DistributedRateLimiter rateLimiter;
    private final DownstreamRouter router;
    private final SyncDownstreamClient syncClient;

    public RequestController(DistributedRateLimiter rateLimiter, DownstreamRouter router, SyncDownstreamClient syncClient) {
        this.rateLimiter = rateLimiter;
        this.router = router;
        this.syncClient = syncClient;
    }

    @PostMapping("/async")
    public ResponseEntity<?> async(@Valid @RequestBody RequestPayload request) {
        // Async requests are rate-limited by the worker immediately before the
        // outbound downstream call. Here we only apply queue backpressure.
        AsyncRequest queued = new AsyncRequest(UUID.randomUUID(), request.downstream(), request.payload(), Instant.now());
        if (!router.enqueue(queued)) {
            return ResponseEntity.status(429).body(Map.of("error", "downstream queue full", "downstream", request.downstream()));
        }
        return ResponseEntity.accepted().body(Map.of("requestId", queued.id(), "status", "QUEUED"));
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync(@Valid @RequestBody RequestPayload request) throws InterruptedException {
        // Synchronous contract: if the global downstream quota is not immediately available,
        // do NOT wait. Return 429 so the caller can retry/back off.
        if (!rateLimiter.tryAcquire(request.downstream())) {
            return ResponseEntity.status(429).body(Map.of("error", "downstream rate limit exceeded", "downstream", request.downstream()));
        }
        String result = syncClient.call(request.downstream(), request.payload());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "result", result));
    }

    @GetMapping("/queues")
    public Map<Downstream, Integer> queues() {
        Map<Downstream, Integer> result = new EnumMap<>(Downstream.class);
        for (Downstream d : Downstream.values()) result.put(d, router.queueSize(d));
        return result;
    }
}
