package com.grip.pipeline.repository;

import com.grip.pipeline.domain.StatusEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads {@code status_events}. The service connects with a privileged Postgres
 * role that bypasses Supabase RLS, so every query is explicitly scoped by
 * {@code userId} to preserve owner isolation.
 */
public interface StatusEventRepository extends JpaRepository<StatusEvent, UUID> {

    /** All transitions for a user, oldest first, so callers can walk each contact's timeline. */
    List<StatusEvent> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
