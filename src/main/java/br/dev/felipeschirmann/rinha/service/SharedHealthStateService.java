package br.dev.felipeschirmann.rinha.service;

import br.dev.felipeschirmann.rinha.model.ProcessorType;
import br.dev.felipeschirmann.rinha.model.dto.HealthState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class SharedHealthStateService {

    private final HashOperations<String, String, String> hashOperations;
    private final ObjectMapper objectMapper;

    public SharedHealthStateService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.hashOperations = redisTemplate.opsForHash();
        this.objectMapper = objectMapper;
    }

    public HealthState getState(ProcessorType type) {
        try {
            String key = "health:state:" + type.name().toLowerCase();
            Map<String, String> entries = hashOperations.entries(key);
            if (entries.isEmpty()) {
                return new HealthState(); // Retorna estado saudável padrão se não existir
            }
            return new HealthState(
                    Integer.parseInt(entries.get("failures")),
                    Instant.parse(entries.get("lastCheckedAt"))
            );
        } catch (Exception e) {
            return new HealthState(); // Em caso de erro, assume estado saudável para não parar o sistema
        }
    }

    public void updateState(ProcessorType type, HealthState state) {
        try {
            String key = "health:state:" + type.name().toLowerCase();
            Map<String, String> map = Map.of(
                    "failures", String.valueOf(state.consecutiveFailures()),
                    "lastCheckedAt", state.lastCheckedAt().toString()
            );
            hashOperations.putAll(key, map);
        } catch (Exception e) {
            // Logar o erro, mas não travar a aplicação
        }
    }
}