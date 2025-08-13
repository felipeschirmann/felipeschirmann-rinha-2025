package br.dev.felipeschirmann.rinha.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ConfigurationBanner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationBanner.class);

    private final RinhaProperties rinhaProperties;
    private final Environment environment;

    public ConfigurationBanner(RinhaProperties rinhaProperties, Environment environment) {
        this.rinhaProperties = rinhaProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String banner = """
                
                \n
                ╔═════════════════════════════════════════════════════════════════════════════╗
                ║                 Configurações Ativas da Aplicação Rinha                     ║
                ╠═════════════════════════════════════════════════════════════════════════════╣
                ║ Spring Profiles Ativos : %-53s ║
                ╠═══════════════════════════╦═════════════════════════════════════════════════╣
                ║ Parâmetro                 ║ Valor                                           ║
                ╠═══════════════════════════╬═════════════════════════════════════════════════╣
                ║ Fila (Queue)              ║                                                 ║
                ║   Tamanho Máximo          ║ %-47d ║
                ╠═══════════════════════════╬═════════════════════════════════════════════════╣
                ║ Executor de Consumidores  ║                                                 ║
                ║   Threads                 ║ %-47s ║
                ╠═══════════════════════════╬═════════════════════════════════════════════════╣
                ║ Agendador de Estratégia   ║                                                 ║
                ║   Período de Decisão      ║ %-47s ║
                ╠═══════════════════════════╬═════════════════════════════════════════════════╣
                ║ Cliente Web (WebClient)   ║                                                 ║
                ║   Conexões Máximas        ║ %-47d ║
                ║   Timeout de Conexão      ║ %-47s ║
                ║   Timeout de Resposta     ║ %-47s ║
                ╠═══════════════════════════╬═════════════════════════════════════════════════╣
                ║ Monitor de Memória        ║                                                 ║
                ║   Limite para Aviso       ║ %-47s ║
                ║   Atraso Inicial          ║ %-47s ║
                ║   Período                 ║ %-47s ║
                ╚═══════════════════════════╩═════════════════════════════════════════════════╝
                """;

        String activeProfiles = Arrays.toString(environment.getActiveProfiles());
        if (activeProfiles.isEmpty()) {
            activeProfiles = Arrays.toString(environment.getDefaultProfiles());
        }

        logger.info(String.format(banner,
                activeProfiles,
                rinhaProperties.queue().maxSize(),
                "Virtual Threads (Dinâmico)",
                rinhaProperties.scheduler().strategyDecisionPeriodMs() + " ms",
                rinhaProperties.webclient().maxConnections(),
                rinhaProperties.webclient().connectTimeoutMs() + " ms",
                rinhaProperties.webclient().responseTimeoutSec() + " s",
                rinhaProperties.memoryMonitor().reportingThresholdMb() + " MB",
                rinhaProperties.memoryMonitor().initialDelaySec() + " s",
                rinhaProperties.memoryMonitor().periodSec() + " s"
        ));
    }
}