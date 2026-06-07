package com.fintech.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "processed_events",
    uniqueConstraints = @UniqueConstraint(name = "uq_processed_events_event_id", columnNames = "event_id")
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {

    // PK = eventId: sem geração automática — o ID vem do payload do Kafka
    @Id
    @Column(name = "event_id", columnDefinition = "uuid", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public static ProcessedEvent of(UUID eventId, String eventType) {
        ProcessedEvent e = new ProcessedEvent();
        e.eventId = eventId;
        e.eventType = eventType;
        e.processedAt = LocalDateTime.now();
        return e;
    }
}
