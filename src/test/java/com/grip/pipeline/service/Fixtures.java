package com.grip.pipeline.service;

import com.grip.pipeline.domain.Contact;
import com.grip.pipeline.domain.PipelineStage;
import com.grip.pipeline.domain.StatusEvent;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Builds read-only domain entities for unit tests. The entities expose no
 * setters (they are JPA read mappings of the Grip schema), so fields are
 * populated reflectively here rather than polluting production code with
 * test-only constructors.
 */
final class Fixtures {

    private Fixtures() {}

    static StatusEvent statusEvent(UUID contactId, PipelineStage stage, Instant at) {
        StatusEvent event = instantiate(StatusEvent.class);
        set(event, "id", UUID.randomUUID());
        set(event, "userId", UUID.randomUUID());
        set(event, "contactId", contactId);
        set(event, "status", stage.dbValue());
        set(event, "createdAt", at);
        return event;
    }

    static Contact dueContact(String name, PipelineStage stage, String action, LocalDate due) {
        Contact contact = instantiate(Contact.class);
        set(contact, "id", UUID.randomUUID());
        set(contact, "userId", UUID.randomUUID());
        set(contact, "name", name);
        set(contact, "status", stage.dbValue());
        set(contact, "nextAction", action);
        set(contact, "nextActionDate", due);
        return contact;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Test fixture failed to instantiate " + type, e);
        }
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Test fixture failed to set " + fieldName, e);
        }
    }
}
