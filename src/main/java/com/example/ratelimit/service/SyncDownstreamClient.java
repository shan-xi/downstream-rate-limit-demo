package com.example.ratelimit.service;

import com.example.ratelimit.config.DownstreamProperties;
import com.example.ratelimit.model.Downstream;
import org.springframework.stereotype.Service;

@Service
public class SyncDownstreamClient {
    private final DownstreamProperties properties;

    public SyncDownstreamClient(DownstreamProperties properties) {
        this.properties = properties;
    }

    public String call(Downstream downstream, String payload) throws InterruptedException {
        Thread.sleep(properties.getRoutes().get(downstream).getLatencyMs());
        return "OK from " + downstream + ": " + payload;
    }
}
