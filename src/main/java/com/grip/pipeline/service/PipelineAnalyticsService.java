package com.grip.pipeline.service;

import com.grip.pipeline.domain.Contact;
import com.grip.pipeline.domain.PipelineStage;
import com.grip.pipeline.domain.StatusEvent;
import com.grip.pipeline.repository.ContactRepository;
import com.grip.pipeline.repository.StatusEventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes hiring-pipeline analytics from {@code status_events} (the exact
 * transition log) and surfaces due follow-ups from {@code contacts}.
 *
 * <p>All reads are scoped by {@code userId}: the service connects with a role
 * that bypasses Supabase RLS, so isolation is enforced here in code.
 */
@Service
@Transactional(readOnly = true)
public class PipelineAnalyticsService {

    private static final int DAYS_SCALE = 2;

    private final StatusEventRepository statusEvents;
    private final ContactRepository contacts;

    public PipelineAnalyticsService(StatusEventRepository statusEvents, ContactRepository contacts) {
        this.statusEvents = statusEvents;
        this.contacts = contacts;
    }

    /**
     * Stage velocity: average days between each pair of consecutive transitions,
     * grouped by (fromStage, toStage). Contacts with a single recorded event
     * contribute nothing, since no dwell interval can be measured.
     */
    public VelocityReport velocity(UUID userId) {
        List<StatusEvent> events = statusEvents.findByUserIdOrderByCreatedAtAsc(userId);

        // Group events per contact, preserving chronological order from the query.
        Map<UUID, List<StatusEvent>> byContact = new java.util.LinkedHashMap<>();
        for (StatusEvent event : events) {
            byContact.computeIfAbsent(event.getContactId(), k -> new ArrayList<>()).add(event);
        }

        Map<TransitionKey, DurationAccumulator> accumulators = new java.util.LinkedHashMap<>();
        for (List<StatusEvent> timeline : byContact.values()) {
            for (int i = 1; i < timeline.size(); i++) {
                StatusEvent from = timeline.get(i - 1);
                StatusEvent to = timeline.get(i);
                TransitionKey key = new TransitionKey(from.getStage(), to.getStage());
                long seconds = Duration.between(from.getCreatedAt(), to.getCreatedAt()).getSeconds();
                accumulators
                        .computeIfAbsent(key, k -> new DurationAccumulator())
                        .add(Math.max(seconds, 0));
            }
        }

        List<VelocityReport.StageVelocity> stages = new ArrayList<>();
        accumulators.forEach(
                (key, acc) ->
                        stages.add(
                                new VelocityReport.StageVelocity(
                                        key.from(), key.to(), acc.avgDays(), acc.count())));
        return new VelocityReport(stages);
    }

    /** Contacts with a non-terminal stage whose follow-up is due on or before {@code asOf}. */
    public List<DueContact> due(UUID userId, LocalDate asOf) {
        List<Contact> dueContacts = contacts.findDue(userId, asOf);
        List<DueContact> result = new ArrayList<>(dueContacts.size());
        for (Contact contact : dueContacts) {
            result.add(DueContact.of(contact, asOf));
        }
        return result;
    }

    private record TransitionKey(PipelineStage from, PipelineStage to) { }

    /** Running sum of dwell seconds for one transition type, kept as a separate concern. */
    private static final class DurationAccumulator {
        private static final long SECONDS_PER_DAY = 86_400L;
        private long totalSeconds;
        private long count;

        void add(long seconds) {
            totalSeconds += seconds;
            count++;
        }

        long count() {
            return count;
        }

        BigDecimal avgDays() {
            if (count == 0) {
                return BigDecimal.ZERO.setScale(DAYS_SCALE, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(totalSeconds)
                    .divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(SECONDS_PER_DAY), DAYS_SCALE, RoundingMode.HALF_UP);
        }
    }
}
