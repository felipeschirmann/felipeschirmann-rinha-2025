package br.dev.felipeschirmann.rinha.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummaryResponse {
    @JsonProperty("default")
    private Summary defaultSummary;

    @JsonProperty("fallback")
    private Summary fallbackSummary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long totalRequests;
        private BigDecimal totalAmount;
    }
}