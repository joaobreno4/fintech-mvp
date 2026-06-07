package com.fintech.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "balance_cents", nullable = false)
    private Long balanceCents;

    @Column(nullable = false)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
