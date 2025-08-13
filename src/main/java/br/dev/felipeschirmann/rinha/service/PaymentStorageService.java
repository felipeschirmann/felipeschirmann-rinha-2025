package br.dev.felipeschirmann.rinha.service;

import br.dev.felipeschirmann.rinha.model.PaymentRequest;
import br.dev.felipeschirmann.rinha.model.dto.VerificationTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentStorageService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentStorageService.class);
    private static final String QUEUE_KEY = "payments:queue";
    private static final String VERIFY_QUEUE_KEY = "payments:verify_queue";

    private final ListOperations<String, String> listOperations;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public PaymentStorageService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.listOperations = redisTemplate.opsForList();
        this.objectMapper = objectMapper;
    }

    public void recordPayment(PaymentRequest paymentRequest) {
        try {
            String jsonPayment = objectMapper.writeValueAsString(paymentRequest);
            listOperations.leftPush(QUEUE_KEY, jsonPayment);
        } catch (Exception e) {
            logger.error("Falha ao enfileirar pagamento no Redis", e);
        }
    }

    public PaymentRequest takePayment() throws InterruptedException {
        try {
            String jsonPayment = listOperations.rightPop(QUEUE_KEY, 0, TimeUnit.SECONDS);
            if (jsonPayment != null) {
                return objectMapper.readValue(jsonPayment, PaymentRequest.class);
            }
        } catch (Exception e) {
            logger.error("Falha ao obter pagamento da fila do Redis", e);
            Thread.sleep(1000); // Pausa antes de tentar de novo
        }
        return null;
    }

    public void enqueueForVerification(VerificationTask task) {
        try {
            String jsonTask = objectMapper.writeValueAsString(task);
            listOperations.leftPush(VERIFY_QUEUE_KEY, jsonTask);
        } catch (Exception e) {
            logger.error("Falha ao enfileirar tarefa de VERIFICAÇÃO no Redis", e);
        }
    }

    public Long getQueueSize() {
        Long size = listOperations.size(QUEUE_KEY);
        return size != null ? size : 0L;
    }

    public VerificationTask takeForVerification() throws InterruptedException {
        try {
            String jsonTask = listOperations.rightPop(VERIFY_QUEUE_KEY, 0, TimeUnit.SECONDS);
            if (jsonTask != null) {
                return objectMapper.readValue(jsonTask, VerificationTask.class);
            }
        } catch (Exception e) {
            logger.error("Falha ao obter tarefa de verificação da fila do Redis", e);
            Thread.sleep(1000); // Pausa antes de tentar de novo
        }
        return null;
    }

    public void purgePayments() {
        redisTemplate.delete(Arrays.asList(QUEUE_KEY, VERIFY_QUEUE_KEY));
        logger.info("Filas de pagamentos no Redis foram limpas.");
    }
}