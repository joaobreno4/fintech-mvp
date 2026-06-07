package com.fintech.transaction.dto;

public record RecoverOutboxResponse(int recovered, String message) {

    public static RecoverOutboxResponse of(int count) {
        String message = count > 0
                ? "%d evento(s) DEAD_LETTER recolocados em PENDING. O relay processará em até 5 segundos.".formatted(count)
                : "Nenhum evento DEAD_LETTER encontrado. Nada a recuperar.";
        return new RecoverOutboxResponse(count, message);
    }
}
