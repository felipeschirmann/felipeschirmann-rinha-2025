package br.dev.felipeschirmann.rinha.web;

import br.dev.felipeschirmann.rinha.model.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class PaymentRouter {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRouter.class);

    @Bean
    public RouterFunction<ServerResponse> paymentRoutes(PaymentHandler paymentHandler, ValidationHandler validationHandler) {
        return route()
                .POST("/payments", accept(MediaType.APPLICATION_JSON),
                        req -> validationHandler.handleRequest(req, PaymentRequest.class, paymentHandler::createPayment))
                .GET("/payments-summary", paymentHandler::getSummary)
                .POST("/purge-payments", paymentHandler::purgePayments)
                .build();

    }

    private HandlerFilterFunction<ServerResponse, ServerResponse> logRequestFilter() {
        return (request, next) -> request.bodyToMono(String.class)
                .flatMap(body -> {
                    logger.trace("Requisição recebida em /payments: {}", body);
                    ServerRequest newRequest = ServerRequest.from(request).body(body).build();
                    return next.handle(newRequest);
                });
    }
}