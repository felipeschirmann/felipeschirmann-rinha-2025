package br.dev.felipeschirmann.rinha.model.dto;

import java.time.Instant;

public record HealthState(
        int consecutiveFailures,
        Instant lastCheckedAt
) {
    public HealthState() {
        this(0, Instant.now());
    }
}