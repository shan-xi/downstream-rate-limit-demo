package com.example.ratelimit.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RequestPayload(@NotNull Downstream downstream, @NotBlank String payload) {
}
