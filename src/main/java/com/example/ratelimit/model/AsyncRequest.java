package com.example.ratelimit.model;

import java.time.Instant;
import java.util.UUID;

public record AsyncRequest(UUID id, Downstream downstream, String payload, Instant acceptedAt) {
}
