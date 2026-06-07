package com.fintech.transaction.repository;

import com.fintech.transaction.domain.OutboxEvent;
import com.fintech.transaction.domain.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    long countByStatus(OutboxStatus status);

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEvent> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(OutboxStatus status, LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT e FROM OutboxEvent e WHERE e.id = :id")
    Optional<OutboxEvent> findByIdWithLock(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = com.fintech.transaction.domain.OutboxStatus.PENDING, " +
           "e.attemptCount = 0, e.nextAttemptAt = :now, e.updatedAt = :now " +
           "WHERE e.status = com.fintech.transaction.domain.OutboxStatus.DEAD_LETTER")
    int resetDeadLetterEvents(@Param("now") LocalDateTime now);
}
