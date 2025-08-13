package br.dev.felipeschirmann.rinha.model.dto;

import br.dev.felipeschirmann.rinha.model.PaymentRequest;
import br.dev.felipeschirmann.rinha.model.ProcessorType;
import java.time.Instant;

// Este objeto carrega todo o contexto necessário para uma verificação de consistência
public record VerificationTask(
        PaymentRequest payment,
        ProcessorType type,
        Instant tentativeTimestamp
) {}