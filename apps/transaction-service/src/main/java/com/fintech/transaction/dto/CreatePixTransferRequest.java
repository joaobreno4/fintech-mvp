package com.fintech.transaction.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreatePixTransferRequest {

    private UUID fromAccountId;
    private UUID toAccountId;
    private Long amountCents;
    private String currency;
}
