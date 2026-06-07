package com.fintech.transaction.dto;

import com.fintech.transaction.domain.TransferStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TransferResponse {

    private UUID id;
    private String idempotencyKey;
    private UUID fromAccountId;
    private UUID toAccountId;
    private Long amountCents;
    private String currency;
    private TransferStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String errorMessage;
}
