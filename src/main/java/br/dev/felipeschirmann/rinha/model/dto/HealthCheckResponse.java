package br.dev.felipeschirmann.rinha.model.dto;

// DTO para receber a resposta do health check
public record HealthCheckResponse(
        boolean failing,
        int minResponseTime
) {}