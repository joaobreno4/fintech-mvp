package com.fintech.account.service;

import com.fintech.account.domain.Account;
import com.fintech.account.domain.ProcessedEvent;
import com.fintech.account.dto.TransferEventPayload;
import com.fintech.account.repository.AccountRepository;
import com.fintech.account.repository.ProcessedEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class AccountService {

    private static final Long INITIAL_BALANCE_CENTS = 1_000_000L;
    private static final String DEFAULT_CURRENCY = "BRL";
    private static final String EVENT_TYPE = "TransferCreated";

    private final AccountRepository accountRepository;
    private final ProcessedEventRepository processedEventRepository;

    public AccountService(AccountRepository accountRepository,
                          ProcessedEventRepository processedEventRepository) {
        this.accountRepository = accountRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void processTransfer(TransferEventPayload payload) {
        UUID eventId = payload.getId();

        // ── Barreira de Idempotência (1ª linha de defesa: leitura rápida) ──────
        // A constraint UNIQUE na tabela é a 2ª linha de defesa contra race conditions.
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.warn("[IDEMPOTENCY] Evento {} já processado. Ignorando duplicidade.", eventId);
            return;
        }

        log.info("[TRANSFER] Processando {} centavos de {} para {}",
                payload.getAmountCents(), payload.getFromAccountId(), payload.getToAccountId());

        Account fromAccount = findOrCreateAccount(payload.getFromAccountId());
        Account toAccount   = findOrCreateAccount(payload.getToAccountId());

        fromAccount.setBalanceCents(fromAccount.getBalanceCents() - payload.getAmountCents());
        toAccount.setBalanceCents(toAccount.getBalanceCents()   + payload.getAmountCents());

        LocalDateTime now = LocalDateTime.now();
        fromAccount.setUpdatedAt(now);
        toAccount.setUpdatedAt(now);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // ── Marca o evento como processado na mesma transação ─────────────────
        // Se o save() de saldo falhar, o ProcessedEvent também não é persistido.
        // Se o INSERT do ProcessedEvent falhar (UNIQUE violation), a transação
        // inteira faz rollback — os saldos não são alterados.
        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.of(eventId, EVENT_TYPE));
        }

        log.info("[TRANSFER] Concluído. Saldos -> origem: {} cts, destino: {} cts",
                fromAccount.getBalanceCents(), toAccount.getBalanceCents());
    }

    private Account findOrCreateAccount(UUID accountId) {
        return accountRepository.findById(accountId).orElseGet(() -> {
            log.info("[ACCOUNT] Conta {} não encontrada. Criando com saldo inicial de {} cts.",
                    accountId, INITIAL_BALANCE_CENTS);
            Account account = new Account();
            account.setId(accountId);
            account.setBalanceCents(INITIAL_BALANCE_CENTS);
            account.setCurrency(DEFAULT_CURRENCY);
            account.setUpdatedAt(LocalDateTime.now());
            return accountRepository.save(account);
        });
    }
}
