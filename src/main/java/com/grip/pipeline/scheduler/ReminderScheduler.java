package com.grip.pipeline.scheduler;

import com.grip.pipeline.notify.ReminderNotifier;
import com.grip.pipeline.repository.ContactRepository;
import com.grip.pipeline.service.DueContact;
import com.grip.pipeline.service.PipelineAnalyticsService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily job that fans out due-follow-up reminders. Each user with a due action
 * gets one {@link ReminderNotifier#notifyDue} call. Failures for one user are
 * logged and isolated so they cannot abort reminders for the rest.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ContactRepository contacts;
    private final PipelineAnalyticsService analytics;
    private final ReminderNotifier notifier;
    private final Clock clock;

    public ReminderScheduler(
            ContactRepository contacts,
            PipelineAnalyticsService analytics,
            ReminderNotifier notifier,
            Clock clock) {
        this.contacts = contacts;
        this.analytics = analytics;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** Runs daily; cron is overridable via {@code grip.reminders.cron}. */
    @Scheduled(cron = "${grip.reminders.cron:0 0 8 * * *}", zone = "${grip.reminders.zone:UTC}")
    public void dispatchDailyReminders() {
        LocalDate today = LocalDate.now(clock);
        List<UUID> users = contacts.findUsersWithDueActions(today);
        log.info("Reminder sweep for {}: {} user(s) with due actions", today, users.size());

        for (UUID userId : users) {
            try {
                List<DueContact> due = analytics.due(userId, today);
                notifier.notifyDue(userId, due);
            } catch (RuntimeException ex) {
                log.error("Failed to dispatch reminders for user {}", userId, ex);
            }
        }
    }
}
