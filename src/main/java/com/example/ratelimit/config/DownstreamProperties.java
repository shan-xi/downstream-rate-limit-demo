package com.example.ratelimit.config;

import com.example.ratelimit.model.Downstream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "downstream")
public class DownstreamProperties {
    private Map<Downstream, Route> routes = new EnumMap<>(Downstream.class);

    public Map<Downstream, Route> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<Downstream, Route> routes) {
        this.routes = routes;
    }

    public static class Route {
        private int rps;
        private int queueCapacity;
        private int workers;
        private long latencyMs;

        public int getRps() {
            return rps;
        }

        public void setRps(int rps) {
            this.rps = rps;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getWorkers() {
            return workers;
        }

        public void setWorkers(int workers) {
            this.workers = workers;
        }

        public long getLatencyMs() {
            return latencyMs;
        }

        public void setLatencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
        }
    }
}
