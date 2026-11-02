package com.example.ratelimit.service;

import com.example.ratelimit.config.DownstreamProperties;
import com.example.ratelimit.model.AsyncRequest;
import com.example.ratelimit.model.Downstream;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class DownstreamRouter {
    private final DownstreamProperties properties;
    private final DistributedRateLimiter rateLimiter;
    private final Map<Downstream, BlockingQueue<AsyncRequest>> queues = new EnumMap<>(Downstream.class);
    private final Map<Downstream, ExecutorService> executors = new EnumMap<>(Downstream.class);

    public DownstreamRouter(DownstreamProperties properties, DistributedRateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        properties.getRoutes().forEach((downstream, route) -> {
            BlockingQueue<AsyncRequest> queue = new ArrayBlockingQueue<>(route.getQueueCapacity());
            queues.put(downstream, queue);
            ExecutorService executor = Executors.newFixedThreadPool(
                    route.getWorkers(),
                    Thread.ofVirtual().factory()
            );
            executors.put(downstream, executor);
            for (int i = 0; i < route.getWorkers(); i++) {
                executor.submit(() -> workerLoop(downstream, queue));
            }
        });
    }

    public boolean enqueue(AsyncRequest request) {
        return queues.get(request.downstream()).offer(request);
    }

    public int queueSize(Downstream downstream) {
        return queues.get(downstream).size();
    }

    /**
     * Spring calls this when the application context is being destroyed.
     * shutdownNow() interrupts workerLoop(), which is important because
     * workers may be blocked in BlockingQueue.take().
     */
    @PreDestroy
    public void shutdown() {
        executors.forEach((downstream, executor) -> {
            System.out.printf("Shutting down workers for downstream=%s%n", downstream);
            executor.shutdownNow();
        });

        executors.forEach((downstream, executor) -> {
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.printf("Workers for downstream=%s did not terminate within 5 seconds%n", downstream);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void workerLoop(Downstream downstream, BlockingQueue<AsyncRequest> queue) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                AsyncRequest request = queue.take();

                // Rate-limit immediately before the outbound call.
                // This makes the Redis/Redisson limit a true global downstream limit
                // across all application instances, rather than only an admission limit.
                while (!rateLimiter.tryAcquire(downstream)) {
                    Thread.sleep(50);
                }

                callDownstream(downstream, request);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void callDownstream(Downstream downstream, AsyncRequest request) throws InterruptedException {
        long latency = properties.getRoutes().get(downstream).getLatencyMs();
        Thread.sleep(latency);
        System.out.printf("instance=%s async -> %s request=%s payload=%s%n",
                System.getenv().getOrDefault("INSTANCE_ID", "local"), downstream, request.id(), request.payload());
    }
}
