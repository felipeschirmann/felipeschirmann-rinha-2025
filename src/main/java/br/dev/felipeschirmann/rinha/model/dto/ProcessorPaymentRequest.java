package br.dev.felipeschirmann.rinha.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// DTO para enviar o pagamento ao processador externo
public record ProcessorPaymentRequest(
        UUID correlationId,
        BigDecimal amount,
//        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant requestedAt // O campo extra exigido pela API externa
) {}