package com.fintech.account.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private static final String SOURCE_TOPIC  = "transaction.events";
    private static final String DLQ_TOPIC     = "transaction.events.DLQ";

    // 2 intervalos de 1s → 3 tentativas totais (1 original + 2 retries)
    private static final long   BACKOFF_INTERVAL_MS = 1_000L;
    private static final long   BACKOFF_MAX_ATTEMPTS = 2L;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ── Tópico DLQ ────────────────────────────────────────────────────────────
    // O KafkaAdmin cria o tópico no broker na inicialização se ele não existir.

    @Bean
    public NewTopic dlqTopic() {
        return TopicBuilder.name(DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ── Recoverer ─────────────────────────────────────────────────────────────
    // Publica a mensagem problemática no tópico DLQ, preservando os headers
    // originais (offset, partition, exception stack trace) para rastreabilidade.

    @Bean
    public DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, String> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // Roteamento explícito: independente do tópico de origem, sempre vai para a DLQ nomeada
                (record, ex) -> {
                    log.error("[DLQ] Mensagem encaminhada para {} após retries esgotados. " +
                              "Tópico origem: {}, Offset: {}, Causa: {}",
                            DLQ_TOPIC, record.topic(), record.offset(), ex.getMessage());
                    return new TopicPartition(DLQ_TOPIC, 0);
                }
        );
    }

    // ── Error Handler ─────────────────────────────────────────────────────────
    // DefaultErrorHandler é a API padrão do Spring Kafka 3.x (Spring Boot 3+).
    // Substitui o legado SeekToCurrentErrorHandler.

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(BACKOFF_INTERVAL_MS, BACKOFF_MAX_ATTEMPTS)
        );

        // Erros não-recuperáveis: vão direto para a DLQ sem consumir as 3 tentativas.
        // JsonProcessingException: payload corrompido/inválido — retry não resolve.
        // IllegalStateException: saldo insuficiente — retry não resolve.
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalStateException.class);

        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("[RETRY] Tentativa {}/{} para offset {} no tópico {}. Erro: {}",
                        deliveryAttempt,
                        BACKOFF_MAX_ATTEMPTS + 1,
                        record.offset(),
                        record.topic(),
                        ex.getMessage())
        );

        return handler;
    }

    // ── Consumer Factory ──────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,           groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }

    // ── Listener Container Factory ────────────────────────────────────────────
    // Sobrescreve o factory auto-configurado pelo Spring Boot.
    // O TransactionConsumer herda o errorHandler sem nenhuma alteração.

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
