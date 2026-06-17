package com.grip.pipeline.repository;

import com.grip.pipeline.domain.Contact;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads {@code contacts}. Scoped by {@code userId} for the same RLS-bypass
 * reason documented on {@link StatusEventRepository}.
 */
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    /**
     * Contacts whose follow-up is due on or before {@code asOf} and that are not
     * in a terminal stage (Offer/Rejected need no further action).
     */
    @Query("""
            select c from Contact c
            where c.userId = :userId
              and c.nextActionDate is not null
              and c.nextActionDate <= :asOf
              and c.status not in ('Offer', 'Rejected')
            order by c.nextActionDate asc
            """)
    List<Contact> findDue(@Param("userId") UUID userId, @Param("asOf") LocalDate asOf);

    /**
     * Distinct users who have at least one non-terminal contact with a follow-up
     * due on or before {@code asOf}. Lets the daily scheduler fan out reminders
     * without an external user registry.
     */
    @Query("""
            select distinct c.userId from Contact c
            where c.nextActionDate is not null
              and c.nextActionDate <= :asOf
              and c.status not in ('Offer', 'Rejected')
            """)
    List<UUID> findUsersWithDueActions(@Param("asOf") LocalDate asOf);
}
