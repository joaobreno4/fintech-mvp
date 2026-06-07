package com.fintech.transaction.service;

import com.fintech.transaction.domain.OutboxEvent;
import com.fintech.transaction.domain.OutboxStatus;
import com.fintech.transaction.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final String TOPIC = "transaction.events";
    private static final int MAX_ATTEMPTS = 5;
    // Timeout máximo aguardando ACK do broker — evita bloqueio indefinido da thread do scheduler
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 30L;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelayService(OutboxEventRepository repository,
                              KafkaTemplate<String, String> kafkaTemplate,
                              MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        // Gauges avaliados no momento do scrape do Prometheus — leitura lazy e sem custo contínuo
        meterRegistry.gauge("outbox.dead.letter.events", repository,
                repo -> repo.countByStatus(OutboxStatus.DEAD_LETTER));
        meterRegistry.gauge("outbox.pending.events", repository,
                repo -> repo.countByStatus(OutboxStatus.PENDING));
        meterRegistry.gauge("outbox.sent.events", repository,
                repo -> repo.countByStatus(OutboxStatus.SENT));
    }

    // Fase 1: lê os candidatos e tenta o envio Kafka FORA de qualquer transação.
    // Isso evita manter uma conexão do pool aberta durante o I/O de rede com o broker,
    // eliminando o gargalo de concorrência e o risco de timeout de conexão.
    @Scheduled(fixedDelay = 5000)
    public void relayEvents() {
        List<OutboxEvent> events = repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                OutboxStatus.PENDING, LocalDateTime.now()
        );

        for (OutboxEvent event : events) {
            // Fase 2: adquire o lock pessimista e persiste o resultado em transação curta,
            // sem I/O de rede dentro dela.
            processEventWithLock(event.getId());
        }
    }

    // REQUIRES_NEW garante transação própria por evento: um erro em um evento
    // não faz rollback dos eventos já processados no mesmo ciclo.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEventWithLock(java.util.UUID eventId) {
        repository.findByIdWithLock(eventId).ifPresent(lockedEvent -> {
            boolean sent = sendToKafka(lockedEvent);
            if (sent) {
                lockedEvent.setStatus(OutboxStatus.SENT);
                lockedEvent.setUpdatedAt(LocalDateTime.now());
                log.info("[OUTBOX] Evento {} enviado com sucesso para o Kafka.", lockedEvent.getId());
            } else {
                handleFailure(lockedEvent);
            }
            repository.save(lockedEvent);
        });
    }

    // Envio síncrono com timeout explícito. Retorna true em sucesso, false em falha.
    // InterruptedException é tratada separadamente para respeitar o contrato de interrupção
    // de threads do Java (re-interrupt antes de retornar).
    private boolean sendToKafka(OutboxEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload())
                         .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            // Restaura o flag de interrupção da thread antes de retornar —
            // sem isso o scheduler poderia ignorar sinais de shutdown da JVM.
            Thread.currentThread().interrupt();
            log.error("[OUTBOX] Thread interrompida ao enviar evento {}.", event.getId(), e);
            return false;
        } catch (ExecutionException e) {
            log.error("[OUTBOX] Falha de broker ao enviar evento {}. Causa: {}",
                      event.getId(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return false;
        } catch (TimeoutException e) {
            log.error("[OUTBOX] Timeout ({}s) ao aguardar ACK do Kafka para evento {}.",
                      KAFKA_SEND_TIMEOUT_SECONDS, event.getId());
            return false;
        }
    }

    @Transactional
    public int recoverDeadLetterEvents() {
        int recovered = repository.resetDeadLetterEvents(LocalDateTime.now());
        log.info("[OUTBOX-RECOVERY] {} evento(s) DEAD_LETTER recolocados em PENDING.", recovered);
        return recovered;
    }

    private void handleFailure(OutboxEvent event) {
        int attempt = event.getAttemptCount() + 1;
        event.setAttemptCount(attempt);
        event.setUpdatedAt(LocalDateTime.now());

        if (attempt >= MAX_ATTEMPTS) {
            // Terminal: para evitar loop eterno consumindo pool do banco
            event.setStatus(OutboxStatus.DEAD_LETTER);
            log.error("[OUTBOX] Evento {} atingiu limite de tentativas. Movido para DEAD_LETTER.", event.getId());
        } else {
            // Recuo exponencial: 2, 4, 8, 16 minutos...
            long backoffMinutes = (long) Math.pow(2, attempt);
            event.setNextAttemptAt(LocalDateTime.now().plusMinutes(backoffMinutes));
            log.warn("[OUTBOX] Evento {} reagendado para {} minutos (Tentativa {}).", event.getId(), backoffMinutes, attempt);
        }
    }
}
