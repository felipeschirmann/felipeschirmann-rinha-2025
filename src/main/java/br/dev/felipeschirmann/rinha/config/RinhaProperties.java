package br.dev.felipeschirmann.rinha.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rinha")
public record RinhaProperties(
        Queue queue,
        Webclient webclient,
        Scheduler scheduler,
        Executor executor,
        MemoryMonitor memoryMonitor,
        Processor processor
) {
    public RinhaProperties {
        if (queue == null) queue = new Queue(50000, 15000);
        if (webclient == null) webclient = new Webclient(2000, 5, 500, 1000);
        if (scheduler == null) scheduler = new Scheduler(3000, 100); // Ex: 1000ms e 100ms
        if (executor == null) executor = new Executor(50);
        if (memoryMonitor == null) memoryMonitor = new MemoryMonitor(50, 5, 5);
        if (processor == null) processor = new Processor(1, 4300);
    }

    public record Queue(
            int maxSize,
            int fallbackTriggerSize
    ) {
    }

    public record Webclient(
            int connectTimeoutMs,
            int responseTimeoutSec,
            int maxConnections,
            int pendingAcquireMaxCount
    ) {
    }

    // Scheduler agora só tem a decisão de estratégia
    public record Scheduler(
            int strategyDecisionPeriodMs,
            int redisBatchPeriodMs
    ) {
    }

    // Novo record específico para o pool de consumidores
    public record Executor(
            int consumerThreads
    ) {
    }

    public record MemoryMonitor(
            long reportingThresholdMb,
            int initialDelaySec,
            int periodSec
    ) {
    }

    public record Processor(
            int failureThreshold,
            long healthDataMaxAgeMs
    ) {
    }
}