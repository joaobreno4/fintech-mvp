package com.fintech.transaction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.transaction.domain.OutboxEvent;
import com.fintech.transaction.domain.OutboxStatus;
import com.fintech.transaction.domain.Transfer;
import com.fintech.transaction.domain.TransferStatus;
import com.fintech.transaction.dto.CreatePixTransferRequest;
import com.fintech.transaction.dto.TransferResponse;
import com.fintech.transaction.repository.OutboxEventRepository;
import com.fintech.transaction.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PixTransferService {

    private final TransferRepository transferRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public PixTransferService(TransferRepository transferRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransferResponse execute(CreatePixTransferRequest request, String idempotencyKey) {
        Optional<Transfer> existingTransfer = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTransfer.isPresent()) {
            return mapToResponse(existingTransfer.get());
        }

        OffsetDateTime nowOffset = OffsetDateTime.now();
        LocalDateTime nowLocal = LocalDateTime.now();

        Transfer transfer = new Transfer();
        UUID transferId = UUID.randomUUID();

        transfer.setId(transferId);
        transfer.setIdempotencyKey(idempotencyKey);
        transfer.setFromAccountId(request.getFromAccountId());
        transfer.setToAccountId(request.getToAccountId());
        transfer.setAmountCents(request.getAmountCents());
        transfer.setCurrency(request.getCurrency());
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setCreatedAt(nowOffset);
        transfer.setUpdatedAt(nowOffset);

        Transfer savedTransfer = transferRepository.save(transfer);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setAggregateType("TRANSFER");
        outboxEvent.setAggregateId(transferId.toString());
        outboxEvent.setEventType("TransferCreated");

        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("id", transferId);
        payloadMap.put("idempotencyKey", idempotencyKey);
        payloadMap.put("fromAccountId", request.getFromAccountId());
        payloadMap.put("toAccountId", request.getToAccountId());
        payloadMap.put("amountCents", request.getAmountCents());
        payloadMap.put("currency", request.getCurrency());
        payloadMap.put("status", TransferStatus.PENDING.name());
        payloadMap.put("createdAt", nowOffset.toString());

        String payload = serializePayload(payloadMap);
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttemptCount(0);
        outboxEvent.setNextAttemptAt(nowLocal);
        outboxEvent.setCreatedAt(nowLocal);
        outboxEvent.setUpdatedAt(nowLocal);

        outboxEventRepository.save(outboxEvent);

        return mapToResponse(savedTransfer);
    }

    private String serializePayload(Map<String, Object> payloadMap) {
        try {
            return objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }

    private TransferResponse mapToResponse(Transfer transfer) {
        TransferResponse response = new TransferResponse();
        response.setId(transfer.getId());
        response.setIdempotencyKey(transfer.getIdempotencyKey());
        response.setFromAccountId(transfer.getFromAccountId());
        response.setToAccountId(transfer.getToAccountId());
        response.setAmountCents(transfer.getAmountCents());
        response.setCurrency(transfer.getCurrency());
        response.setStatus(transfer.getStatus());
        response.setCreatedAt(transfer.getCreatedAt());
        response.setUpdatedAt(transfer.getUpdatedAt());
        response.setErrorMessage(transfer.getErrorMessage());
        return response;
    }
}
