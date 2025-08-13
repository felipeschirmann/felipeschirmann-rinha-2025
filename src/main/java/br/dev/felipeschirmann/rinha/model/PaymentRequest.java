package br.dev.felipeschirmann.rinha.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotNull UUID correlationId,
        @NotNull BigDecimal amount
) {
}