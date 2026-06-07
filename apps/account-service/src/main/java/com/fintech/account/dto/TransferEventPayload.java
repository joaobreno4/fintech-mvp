package com.fintech.account.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferEventPayload {

    // Mapeado do campo "id" do payload JSON gerado pelo PixTransferService
    private UUID id;

    private UUID fromAccountId;
    private UUID toAccountId;
    private Long amountCents;
    private String currency;
}
