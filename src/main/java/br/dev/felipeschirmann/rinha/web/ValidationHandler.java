package br.dev.felipeschirmann.rinha.web;

import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import java.util.function.Function;

@Component
public class ValidationHandler {

    private final Validator validator;

    public ValidationHandler(Validator validator) {
        this.validator = validator;
    }

    public <T> Mono<ServerResponse> handleRequest(
            ServerRequest request,
            Class<T> bodyClass,
            Function<T, Mono<ServerResponse>> validBodyHandler) {

        return request.bodyToMono(bodyClass)
                .flatMap(body -> {
                    var violations = validator.validate(body);
                    if (violations.isEmpty()) {
                        return validBodyHandler.apply(body);
                    } else {
                        // Se houver erros de validação, retorna 400 Bad Request
                        return Mono.error(new ServerWebInputException(violations.toString()));
                    }
                });
    }
}