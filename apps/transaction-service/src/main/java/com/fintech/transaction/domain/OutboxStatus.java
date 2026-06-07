package com.fintech.transaction.domain;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD_LETTER
}
