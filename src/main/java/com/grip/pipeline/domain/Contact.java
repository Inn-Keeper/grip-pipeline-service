package com.grip.pipeline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only mapping of the Grip {@code contacts} table (migration 0001_init.sql).
 * This service never writes contacts; the React/React-Native clients own that.
 */
@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    private String role;

    private String link;

    @Column(name = "next_action")
    private String nextAction;

    @Column(name = "next_action_date")
    private LocalDate nextActionDate;

    protected Contact() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public PipelineStage getStage() {
        return PipelineStage.fromDbValue(status);
    }

    public String getRole() {
        return role;
    }

    public String getLink() {
        return link;
    }

    public String getNextAction() {
        return nextAction;
    }

    public LocalDate getNextActionDate() {
        return nextActionDate;
    }
}
