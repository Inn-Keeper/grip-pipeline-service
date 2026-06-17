package com.grip.pipeline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only mapping of the Grip {@code status_events} table (migration
 * 0003_status_events.sql). One row per contact status transition, written by a
 * Postgres trigger so the funnel is exact rather than approximated.
 *
 * <p>This is the source of truth for both funnel conversion and stage velocity.
 */
@Entity
@Table(name = "status_events")
public class StatusEvent {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StatusEvent() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getContactId() {
        return contactId;
    }

    public PipelineStage getStage() {
        return PipelineStage.fromDbValue(status);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
