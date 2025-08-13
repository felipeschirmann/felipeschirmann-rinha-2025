package br.dev.felipeschirmann.rinha.model.dto;

import java.math.BigDecimal;
import java.time.Instant;

// Guarda a informação essencial de um pagamento bem-sucedido para o sumário
public record SuccessfulPayment(
        BigDecimal amount,
        Instant timestamp
) {
}