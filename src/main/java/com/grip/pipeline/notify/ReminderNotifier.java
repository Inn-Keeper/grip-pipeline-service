package com.grip.pipeline.notify;

import com.grip.pipeline.service.DueContact;
import java.util.List;
import java.util.UUID;

/**
 * Sink for due-follow-up reminders produced by the daily scheduler. Kept as an
 * interface so the logging implementation can be swapped for email/push without
 * touching the scheduler.
 */
public interface ReminderNotifier {

    void notifyDue(UUID userId, List<DueContact> due);
}
