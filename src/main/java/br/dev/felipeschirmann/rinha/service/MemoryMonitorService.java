package br.dev.felipeschirmann.rinha.service;

import br.dev.felipeschirmann.rinha.config.RinhaProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class MemoryMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMonitorService.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final long MEGABYTE = 1024L * 1024L;

    private final RinhaProperties rinhaProperties;
    private long reportingThresholdMb;
    private long lastReportedThreshold = 0;

    public MemoryMonitorService(RinhaProperties rinhaProperties) {
        this.rinhaProperties = rinhaProperties;
    }

    @PostConstruct
    public void startMonitor() {
        this.reportingThresholdMb = rinhaProperties.memoryMonitor().reportingThresholdMb();
        int initialDelay = rinhaProperties.memoryMonitor().initialDelaySec();
        int period = rinhaProperties.memoryMonitor().periodSec();

        logger.info("Iniciando monitor de memória... Verificação a cada {} segundos.", period);
        scheduler.scheduleAtFixedRate(this::monitorMemory, initialDelay, period, TimeUnit.SECONDS);
    }

    private void monitorMemory() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long usedHeapMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long usedHeapMB = usedHeapMemory / MEGABYTE;

            long currentThreshold = (usedHeapMB / this.reportingThresholdMb) * this.reportingThresholdMb;

            if (currentThreshold > lastReportedThreshold) {
                logger.warn("AVISO DE MEMÓRIA: Uso de Heap ultrapassou {} MB. Uso atual: {} MB", currentThreshold, usedHeapMB);
                this.lastReportedThreshold = currentThreshold;
            }
        } catch (Exception e) {
            logger.error("Erro ao monitorar a memória", e);
        }
    }

    @PreDestroy
    public void stopMonitor() {
        scheduler.shutdown();
    }
}