package br.dev.felipeschirmann.rinha.service;

import br.dev.felipeschirmann.rinha.model.PaymentSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentSummaryService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentSummaryService.class);
    private static final String KEY_DEFAULT = "payments:default";
    private static final String KEY_FALLBACK = "payments:fallback";

    private final ZSetOperations<String, String> zSetOperations;
    private final RedisTemplate<String, String> redisTemplate;

    public PaymentSummaryService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.zSetOperations = redisTemplate.opsForZSet();
    }

    // A ESCRITA AGORA É DIRETA E IMEDIATA
    public void recordSuccessfulDefaultPayment(BigDecimal amount, Instant requestedAt) {
        // Sem buffer, sem agendador. Escrevemos diretamente no Redis.
        zSetOperations.add(KEY_DEFAULT, amount.toString() + ":" + UUID.randomUUID(), requestedAt.toEpochMilli());
    }

    public void recordSuccessfulFallbackPayment(BigDecimal amount, Instant requestedAt) {
        zSetOperations.add(KEY_FALLBACK, amount.toString() + ":" + UUID.randomUUID(), requestedAt.toEpochMilli());
    }

    public Mono<PaymentSummaryResponse> getSummary(Optional<Instant> from, Optional<Instant> to) {
        // A lógica de leitura não precisa mais esvaziar um buffer. Ela apenas lê.
        return Mono.fromCallable(() -> {
                    var defaultSummary = calculateSummaryFor(KEY_DEFAULT, from, to);
                    var fallbackSummary = calculateSummaryFor(KEY_FALLBACK, from, to);
                    return new PaymentSummaryResponse(defaultSummary, fallbackSummary);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private PaymentSummaryResponse.Summary calculateSummaryFor(String key, Optional<Instant> from, Optional<Instant> to) {
        long start = from.map(Instant::toEpochMilli).orElse(Long.MIN_VALUE);
        long end = to.map(Instant::toEpochMilli).orElse(Long.MAX_VALUE);

        // A lógica de cálculo permanece a mesma
        Set<String> payments = zSetOperations.rangeByScore(key, start, end);
        if (payments == null || payments.isEmpty()) {
            return new PaymentSummaryResponse.Summary(0L, BigDecimal.ZERO);
        }

        long totalRequests = payments.size();
        BigDecimal totalAmount = payments.stream()
                .map(paymentString -> new BigDecimal(paymentString.split(":")[0]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PaymentSummaryResponse.Summary(totalRequests, totalAmount);
    }

    public void purgePayments() {
        // A limpeza do Redis continua a mesma
        redisTemplate.delete(Arrays.asList(KEY_DEFAULT, KEY_FALLBACK));
    }
}