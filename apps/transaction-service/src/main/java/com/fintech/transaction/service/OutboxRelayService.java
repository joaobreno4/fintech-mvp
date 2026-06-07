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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final String TOPIC = "transaction.events";
    private static final int MAX_ATTEMPTS = 5;

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

    // A mágica que faltava: Abre a transação antes do lock pessimista!
    @Scheduled(fixedDelay = 5000)
    @Transactional 
    public void relayEvents() {
        // Puxa do banco apenas PENDING com o next_attempt_at vencido
        List<OutboxEvent> events = repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                OutboxStatus.PENDING, LocalDateTime.now()
        );

        for (OutboxEvent event : events) {
            // Emite o SELECT FOR UPDATE SKIP LOCKED via repositório
            repository.findByIdWithLock(event.getId()).ifPresent(lockedEvent -> {
                try {
                    // Envia para o Kafka usando o aggregateId (UUID da transação) como Chave de Partição
                    // O .get() força o envio síncrono, respeitando o acks=all
                    kafkaTemplate.send(TOPIC, lockedEvent.getAggregateId(), lockedEvent.getPayload()).get();
                    
                    lockedEvent.setStatus(OutboxStatus.SENT);
                    lockedEvent.setUpdatedAt(LocalDateTime.now());
                    log.info("[OUTBOX] Evento {} enviado com sucesso para o Kafka.", lockedEvent.getId());
                    
                } catch (Exception e) {
                    log.error("[OUTBOX] Falha de rede ao enviar evento {} para o Kafka.", lockedEvent.getId(), e);
                    handleFailure(lockedEvent);
                }
                
                // O Hibernate gerencia o flush automático no fim da transação
                repository.save(lockedEvent);
            });
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
