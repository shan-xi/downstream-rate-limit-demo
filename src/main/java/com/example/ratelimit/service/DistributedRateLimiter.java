package com.example.ratelimit.service;

import com.example.ratelimit.config.DownstreamProperties;
import com.example.ratelimit.model.Downstream;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class DistributedRateLimiter {
    private final RedissonClient redisson;
    private final DownstreamProperties properties;

    public DistributedRateLimiter(RedissonClient redisson, DownstreamProperties properties) {
        this.redisson = redisson;
        this.properties = properties;
    }

    public boolean tryAcquire(Downstream downstream) {
        var route = properties.getRoutes().get(downstream);
        if (route == null) {
            throw new IllegalArgumentException("No rate-limit configuration for downstream: " + downstream);
        }
        if (route.getRps() <= 0) {
            throw new IllegalStateException("RPS must be greater than 0 for downstream: " + downstream);
        }

        String key = "downstream:rate:" + downstream;
        RRateLimiter limiter = redisson.getRateLimiter(key);

        // OVERALL means the limit is shared by all application instances.
        // Example: A = 5 RPS and 3 app instances still means 5 RPS total.
        limiter.trySetRate(
                RateType.OVERALL,
                route.getRps(),
                1,
                RateIntervalUnit.SECONDS
        );

        return limiter.tryAcquire(1);
    }
}
