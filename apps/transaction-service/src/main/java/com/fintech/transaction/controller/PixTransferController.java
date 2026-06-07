package com.fintech.transaction.controller;

import com.fintech.transaction.dto.CreatePixTransferRequest;
import com.fintech.transaction.dto.TransferResponse;
import com.fintech.transaction.service.PixTransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/transfers")
public class PixTransferController {

    private final PixTransferService pixTransferService;

    public PixTransferController(PixTransferService pixTransferService) {
        this.pixTransferService = pixTransferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @RequestBody CreatePixTransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        TransferResponse response = pixTransferService.execute(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/transfers/" + response.getId())).body(response);
    }
}
