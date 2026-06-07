package com.fintech.account.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.account.dto.TransferEventPayload;
import com.fintech.account.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionConsumer {

    private static final String TOPIC = "transaction.events";
    private static final String GROUP_ID = "account-service-group";

    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    public TransactionConsumer(AccountService accountService, ObjectMapper objectMapper) {
        this.accountService = accountService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consume(ConsumerRecord<String, String> record) {
        log.info("Received Kafka message. Topic: {}, Partition: {}, Offset: {}, Key: {}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            TransferEventPayload payload = objectMapper.readValue(record.value(), TransferEventPayload.class);
            accountService.processTransfer(payload);
        } catch (Exception e) {
            log.error("Failed to process Kafka message. Key: {}, Payload: {}. Error: {}",
                    record.key(), record.value(), e.getMessage(), e);
            throw new RuntimeException("Kafka message processing failed", e);
        }
    }
}
