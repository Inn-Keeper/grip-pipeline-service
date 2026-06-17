package com.grip.pipeline.service;

import com.grip.pipeline.domain.Contact;
import com.grip.pipeline.domain.PipelineStage;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A contact whose follow-up action is due. {@code overdueDays} is positive when
 * the action date has already passed, zero when it is due today.
 */
public record DueContact(
        UUID contactId,
        String name,
        PipelineStage stage,
        String nextAction,
        LocalDate nextActionDate,
        long overdueDays) {

    static DueContact of(Contact contact, LocalDate asOf) {
        long overdue = java.time.temporal.ChronoUnit.DAYS.between(contact.getNextActionDate(), asOf);
        return new DueContact(
                contact.getId(),
                contact.getName(),
                contact.getStage(),
                contact.getNextAction(),
                contact.getNextActionDate(),
                overdue);
    }
}
