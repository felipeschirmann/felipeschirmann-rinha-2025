package br.dev.felipeschirmann.rinha.service;

import br.dev.felipeschirmann.rinha.config.RinhaProperties;
import br.dev.felipeschirmann.rinha.model.PaymentRequest;
import br.dev.felipeschirmann.rinha.model.ProcessorType;
import br.dev.felipeschirmann.rinha.model.dto.HealthCheckResponse;
import br.dev.felipeschirmann.rinha.model.dto.HealthState;
import br.dev.felipeschirmann.rinha.model.dto.ProcessorPaymentRequest;
import br.dev.felipeschirmann.rinha.model.dto.VerificationTask;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PaymentProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentProcessorService.class);
    private static final Logger paymentTraceLogger = LoggerFactory.getLogger("PaymentTrace");

    private final ScheduledExecutorService healthScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService strategyScheduler = Executors.newSingleThreadScheduledExecutor();
    private ExecutorService consumerExecutor;
    private ExecutorService verificationExecutor;

    private volatile ProcessorType preferredProcessor = ProcessorType.DEFAULT;

    private final RestClient defaultRestClient;
    private final RestClient fallbackRestClient;
    private final PaymentStorageService storageService;
    private final PaymentSummaryService summaryService;
    private final CircuitBreaker defaultCb;
    private final CircuitBreaker fallbackCb;
    private final RinhaProperties rinhaProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final SharedHealthStateService healthStateService;

    public PaymentProcessorService(RestClient.Builder restClientBuilder,
                                   PaymentStorageService storageService,
                                   PaymentSummaryService summaryService,
                                   @Value("${processor.default.url}") String defaultUrl,
                                   @Value("${processor.fallback.url}") String fallbackUrl,
                                   CircuitBreaker defaultProcessorCircuitBreaker,
                                   CircuitBreaker fallbackProcessorCircuitBreaker,
                                   RedisTemplate<String, String> redisTemplate,
                                   SharedHealthStateService healthStateService,
                                   RinhaProperties rinhaProperties) {
        this.storageService = storageService;
        this.summaryService = summaryService;
        this.defaultCb = defaultProcessorCircuitBreaker;
        this.fallbackCb = fallbackProcessorCircuitBreaker;
        this.rinhaProperties = rinhaProperties;
        this.defaultRestClient = restClientBuilder.baseUrl(defaultUrl).build();
        this.fallbackRestClient = restClientBuilder.baseUrl(fallbackUrl).build();
        this.redisTemplate = redisTemplate;
        this.healthStateService = healthStateService;
    }

    @PostConstruct
    public void initialize() {
        logger.info("Iniciando processador de pagamentos com estratégia adaptativa...");
        healthScheduler.scheduleAtFixedRate(this::checkDefaultHealth, 0, 5, TimeUnit.SECONDS);
        healthScheduler.scheduleAtFixedRate(this::checkFallbackHealth, 1, 5, TimeUnit.SECONDS);

        int strategyPeriod = rinhaProperties.scheduler().strategyDecisionPeriodMs();
        strategyScheduler.scheduleAtFixedRate(this::decideStrategy, 1, strategyPeriod, TimeUnit.MILLISECONDS);

        consumerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        consumerExecutor.submit(this::dispatcherLoop);

        verificationExecutor = Executors.newVirtualThreadPerTaskExecutor();
        verificationExecutor.submit(this::verificationLoop);
    }

    private void dispatcherLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                PaymentRequest payment = storageService.takePayment();
                if (payment != null) {
                    consumerExecutor.submit(() -> processPayment(payment));
                }
            } catch (InterruptedException e) {
                logger.warn("Thread despachante interrompida. Desligando...");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void verificationLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                VerificationTask task = storageService.takeForVerification();
                if (task != null) {
                    verificationExecutor.submit(() ->
                            verifyPaymentConsistency(task.payment(), task.type(), task.tentativeTimestamp())
                    );
                }
            } catch (InterruptedException e) {
                logger.warn("Thread de verificação interrompida. Desligando...");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processPayment(PaymentRequest payment) {
        ProcessorType type = this.preferredProcessor;

        long queueSize = storageService.getQueueSize();
        HealthState fallbackState = healthStateService.getState(ProcessorType.FALLBACK);
        if (type == ProcessorType.DEFAULT && queueSize > rinhaProperties.queue().fallbackTriggerSize()) {
            if (fallbackState.consecutiveFailures() < rinhaProperties.processor().failureThreshold()) {
                type = ProcessorType.FALLBACK;
                paymentTraceLogger.debug("ROTA: DEFAULT sobrecarregado (fila {}). Usando FALLBACK para {}.", queueSize, payment.correlationId());
            }
        }

        CircuitBreaker cb = (type == ProcessorType.DEFAULT) ? defaultCb : fallbackCb;
        RestClient client = (type == ProcessorType.DEFAULT) ? defaultRestClient : fallbackRestClient;
        var processorRequest = new ProcessorPaymentRequest(payment.correlationId(), payment.amount(), Instant.now());

        try {
            cb.executeRunnable(() -> {
                client.post().uri("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(processorRequest)
                        .retrieve()
                        .toBodilessEntity();
            });

            paymentTraceLogger.debug("SUCESSO: Pagamento {} processado pelo {}.", payment.correlationId(), type);
            if (type == ProcessorType.DEFAULT) {
                summaryService.recordSuccessfulDefaultPayment(payment.amount(), processorRequest.requestedAt());
            } else {
                summaryService.recordSuccessfulFallbackPayment(payment.amount(), processorRequest.requestedAt());
            }
        } catch (CallNotPermittedException e) {
            requeuePayment(payment, "circuit breaker para " + type + " aberto");
        } catch (HttpServerErrorException e) {
            paymentTraceLogger.debug("FALHA (5xx): Servidor {} retornou erro {}. Verificando consistência para o pagamento {}...", type, e.getStatusCode().value(), payment.correlationId());
            verifyPaymentConsistency(payment, type, processorRequest.requestedAt());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                logger.error("ERRO IRRECUPERÁVEL (400) no {}: Pagamento {} descartado.", type, payment.correlationId());
            } else {
                logger.error("Erro de cliente INESPERADO ({}) no {}: Pagamento {} foi descartado ou precisa de análise.", e.getStatusCode().value(), type, payment.correlationId());
            }
        } catch (ResourceAccessException e) {
            paymentTraceLogger.debug("FALHA (Rede): Pagamento {} encontrou '{}'. Verificando consistência...", payment.correlationId(), e.getClass().getSimpleName());
            verifyPaymentConsistency(payment, type, processorRequest.requestedAt());
        } catch (Exception e) {
            logger.error("Erro GENÉRICO INESPERADO ao processar {}. Verificando consistência...", payment.correlationId(), e);
            verifyPaymentConsistency(payment, type, processorRequest.requestedAt());
        }
    }

    private void decideStrategy() {
        ProcessorType currentPreference = this.preferredProcessor;
        int failureThreshold = rinhaProperties.processor().failureThreshold();
        long maxAge = rinhaProperties.processor().healthDataMaxAgeMs();
        Instant now = Instant.now();

        // Obtém o estado de saúde e calcula a idade da informação para cada processador
        HealthState defaultState = healthStateService.getState(ProcessorType.DEFAULT);
        long defaultAge = Duration.between(defaultState.lastCheckedAt(), now).toMillis();

        HealthState fallbackState = healthStateService.getState(ProcessorType.FALLBACK);
        long fallbackAge = Duration.between(fallbackState.lastCheckedAt(), now).toMillis();

        // Um processador só é considerado "confiável" se não tiver falhas E se sua informação for recente
        boolean isDefaultReliable = defaultState.consecutiveFailures() < failureThreshold && defaultAge < maxAge;
        boolean isFallbackReliable = fallbackState.consecutiveFailures() < failureThreshold && fallbackAge < maxAge;

        if (isDefaultReliable) {
            // Regra #1: Se o default é confiável, ele é o preferido.
            this.preferredProcessor = ProcessorType.DEFAULT;
            if (currentPreference != ProcessorType.DEFAULT) {
                logger.warn("ESTRATÉGIA: Processador DEFAULT confiável. Roteando preferencialmente para DEFAULT.");
            }
        } else if (isFallbackReliable) {
            // Regra #2: Se o default não é confiável, mas o fallback é, usamos o fallback.
            this.preferredProcessor = ProcessorType.FALLBACK;
            if (currentPreference != ProcessorType.FALLBACK) {
                logger.warn("ESTRATÉGIA: Processador DEFAULT não confiável. Roteando preferencialmente para FALLBACK.");
            }
        } else {
            // Regra #3: Se nenhum é confiável, mantemos a esperança no default e deixamos o Circuit Breaker agir.
            this.preferredProcessor = ProcessorType.DEFAULT;
            if (currentPreference != ProcessorType.DEFAULT) {
                logger.error("ESTRATÉGIA: NENHUM processador confiável. Mantendo preferência no DEFAULT.");
            }
        }
    }

    private void requeuePayment(PaymentRequest payment, String reason) {
        paymentTraceLogger.debug("REENFILEIRADO: Pagamento {} devolvido para a fila. Motivo: {}.", payment.correlationId(), reason);
        storageService.recordPayment(payment);
    }

    private void verifyPaymentConsistency(PaymentRequest payment, ProcessorType type, Instant tentativeTimestamp) {
        RestClient client = (type == ProcessorType.DEFAULT) ? defaultRestClient : fallbackRestClient;
        int maxRetries = 3;
        long backoffDelayMs = 100;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            HealthState targetState = healthStateService.getState(type);
            if (targetState.consecutiveFailures() >= rinhaProperties.processor().failureThreshold()) {
                paymentTraceLogger.debug("CONSISTÊNCIA: Abortando verificação para {} pois o processador {} já está offline.", payment.correlationId(), type);
                storageService.enqueueForVerification(new VerificationTask(payment, type, tentativeTimestamp));
                return;
            }

            try {
                client.get().uri("/payments/{id}", payment.correlationId()).retrieve().toBodilessEntity();
                paymentTraceLogger.debug("CONSISTÊNCIA-OK (tentativa {}/{}): Pagamento {} foi processado no {}. Contabilizando.", attempt, maxRetries, payment.correlationId(), type);
                if (type == ProcessorType.DEFAULT) {
                    summaryService.recordSuccessfulDefaultPayment(payment.amount(), tentativeTimestamp);
                } else {
                    summaryService.recordSuccessfulFallbackPayment(payment.amount(), tentativeTimestamp);
                }
                return;
            } catch (HttpClientErrorException.NotFound e) {
                paymentTraceLogger.debug("CONSISTÊNCIA-FALHA (tentativa {}/{}): Pagamento {} não localizado no {}. Reenfileirando.", attempt, maxRetries, payment.correlationId(), type);
                requeuePayment(payment, "não localizado na consistência");
                return;
            } catch (Exception e) {
                paymentTraceLogger.debug("CONSISTÊNCIA-ERRO (tentativa {}/{}): Erro ao verificar {}. Tentando novamente...", attempt, maxRetries, payment.correlationId());
                if (attempt == maxRetries) {
                    logger.error("CONSISTÊNCIA: Todas as {} tentativas de verificação para {} falharam. Movendo para fila de verificação.", maxRetries, payment.correlationId());
                    storageService.enqueueForVerification(new VerificationTask(payment, type, tentativeTimestamp));
                } else {
                    try {
                        Thread.sleep(backoffDelayMs);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        logger.error("Thread de verificação interrompida. Movendo para fila de verificação {}.", payment.correlationId());
                        storageService.enqueueForVerification(new VerificationTask(payment, type, tentativeTimestamp));
                        break;
                    }
                }
            }
        }
    }

    private void checkDefaultHealth() {
        String lockKey = "health_lock:default";
        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(4));
        if (Boolean.TRUE.equals(acquiredLock)) {
            HealthState currentState = healthStateService.getState(ProcessorType.DEFAULT);
            try {
                HealthCheckResponse health = defaultRestClient.get().uri("/payments/service-health").retrieve().body(HealthCheckResponse.class);
                if (health != null && !health.failing()) {
                    healthStateService.updateState(ProcessorType.DEFAULT, new HealthState(0, Instant.now()));
                    if (currentState.consecutiveFailures() > 0) logger.info("Health Check: Default recuperado.");
                } else {
                    healthStateService.updateState(ProcessorType.DEFAULT, new HealthState(currentState.consecutiveFailures() + 1, Instant.now()));
                    logger.warn("Health Check: Default COM FALHAS (falha consecutiva #{})", currentState.consecutiveFailures() + 1);
                }
            } catch (Exception e) {
                healthStateService.updateState(ProcessorType.DEFAULT, new HealthState(currentState.consecutiveFailures() + 1, Instant.now()));
                logger.error("Health Check: Falha ao contatar o Processador Default (falha consecutiva #{})", currentState.consecutiveFailures() + 1);
            }
        }
    }

    private void checkFallbackHealth() {
        String lockKey = "health_lock:fallback"; // Corrigido
        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(4));
        if (Boolean.TRUE.equals(acquiredLock)) {
            HealthState currentState = healthStateService.getState(ProcessorType.FALLBACK); // Corrigido
            try {
                HealthCheckResponse health = fallbackRestClient.get().uri("/payments/service-health").retrieve().body(HealthCheckResponse.class); // Corrigido
                if (health != null && !health.failing()) {
                    healthStateService.updateState(ProcessorType.FALLBACK, new HealthState(0, Instant.now())); // Corrigido
                    if (currentState.consecutiveFailures() > 0) logger.info("Health Check: Fallback recuperado.");
                } else {
                    healthStateService.updateState(ProcessorType.FALLBACK, new HealthState(currentState.consecutiveFailures() + 1, Instant.now())); // Corrigido
                    logger.warn("Health Check: Fallback COM FALHAS (falha consecutiva #{})", currentState.consecutiveFailures() + 1);
                }
            } catch (Exception e) {
                healthStateService.updateState(ProcessorType.FALLBACK, new HealthState(currentState.consecutiveFailures() + 1, Instant.now())); // Corrigido
                logger.error("Health Check: Falha ao contatar o Processador Fallback (falha consecutiva #{})", currentState.consecutiveFailures() + 1);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Iniciando desligamento gracioso...");
        healthScheduler.shutdown();
        strategyScheduler.shutdown();
        if (consumerExecutor != null) {
            shutdownExecutor(consumerExecutor, "Consumidor Principal");
        }
        if (verificationExecutor != null) {
            shutdownExecutor(verificationExecutor, "Consumidor de Verificação");
        }
        logger.info("Agendadores e Executores finalizados.");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Timeout no desligamento do executor '{}': algumas tarefas podem não ter sido finalizadas.", name);
                executor.shutdownNow();
            } else {
                logger.info("Executor '{}' finalizado com sucesso.", name);
            }
        } catch (InterruptedException e) {
            logger.error("Thread de desligamento foi interrompida para o executor '{}'.", name, e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}