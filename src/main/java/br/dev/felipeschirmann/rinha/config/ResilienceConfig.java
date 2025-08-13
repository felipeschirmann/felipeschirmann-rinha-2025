package br.dev.felipeschirmann.rinha.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker defaultProcessorCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("default-processor");
    }

    @Bean
    public CircuitBreaker fallbackProcessorCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("fallback-processor");
    }
}