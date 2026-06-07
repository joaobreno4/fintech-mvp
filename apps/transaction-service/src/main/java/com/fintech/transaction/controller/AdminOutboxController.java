package com.fintech.transaction.controller;

import com.fintech.transaction.dto.RecoverOutboxResponse;
import com.fintech.transaction.service.OutboxRelayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/outbox")
public class AdminOutboxController {

    private final OutboxRelayService outboxRelayService;

    public AdminOutboxController(OutboxRelayService outboxRelayService) {
        this.outboxRelayService = outboxRelayService;
    }

    /**
     * Recoloca todos os eventos DEAD_LETTER em PENDING com attempt_count=0,
     * permitindo que o relay os reprocesse após uma recuperação de infraestrutura (ex: Kafka voltou).
     * Operação idempotente: segura para chamar múltiplas vezes.
     */
    @PostMapping("/recover")
    public ResponseEntity<RecoverOutboxResponse> recoverDeadLetterEvents() {
        int recovered = outboxRelayService.recoverDeadLetterEvents();
        return ResponseEntity.ok(RecoverOutboxResponse.of(recovered));
    }
}
