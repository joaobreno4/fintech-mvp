package com.fintech.account.repository;

import com.fintech.account.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    // existsById herdado do JpaRepository — query otimizada: SELECT 1 FROM processed_events WHERE event_id = ?
}
