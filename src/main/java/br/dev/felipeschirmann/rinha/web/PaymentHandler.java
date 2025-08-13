package br.dev.felipeschirmann.rinha.web;

import br.dev.felipeschirmann.rinha.model.PaymentRequest;
import br.dev.felipeschirmann.rinha.service.PaymentStorageService;
import br.dev.felipeschirmann.rinha.service.PaymentSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
public class PaymentHandler {

    private final PaymentStorageService storageService;
    private final PaymentSummaryService summaryService;
    private static final Logger logger = LoggerFactory.getLogger(PaymentHandler.class);

    public PaymentHandler(PaymentStorageService storageService, PaymentSummaryService summaryService) {
        this.storageService = storageService;
        this.summaryService = summaryService;
    }

    public Mono<ServerResponse> createPayment(PaymentRequest paymentRequest) {
        storageService.recordPayment(paymentRequest);
        return ServerResponse.accepted().build();
    }

    /**
     * Lida com as requisições do sumário.
     * Neste passo, sempre retorna um sumário zerado.
     */
    public Mono<ServerResponse> getSummary(ServerRequest request) {
        Optional<Instant> from = request.queryParam("from").map(Instant::parse);
        Optional<Instant> to = request.queryParam("to").map(Instant::parse);

        return summaryService.getSummary(from, to)
                .flatMap(summaryResponse ->
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(summaryResponse)
                );
    }

    public Mono<ServerResponse> purgePayments(ServerRequest request) {
        logger.warn("Recebida requisição para PURGAR todos os dados de pagamento.");
        storageService.purgePayments();
        summaryService.purgePayments();

        Map<String, String> responseBody = Map.of("message", "All payments purged.");

        // Retorna HTTP 200 OK com o corpo da resposta
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(responseBody);
    }

}