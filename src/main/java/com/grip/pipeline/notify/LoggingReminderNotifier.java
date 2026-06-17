package com.grip.pipeline.notify;

import com.grip.pipeline.service.DueContact;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default reminder sink: logs each due follow-up. Demo-friendly, no external deps. */
@Component
public class LoggingReminderNotifier implements ReminderNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingReminderNotifier.class);

    @Override
    public void notifyDue(UUID userId, List<DueContact> due) {
        if (due.isEmpty()) {
            log.info("No follow-ups due for user {}", userId);
            return;
        }
        log.info("{} follow-up(s) due for user {}:", due.size(), userId);
        for (DueContact contact : due) {
            log.info(
                    "  • {} [{}] — {} (due {}, {} day(s) overdue)",
                    contact.name(),
                    contact.stage().dbValue(),
                    contact.nextAction(),
                    contact.nextActionDate(),
                    contact.overdueDays());
        }
    }
}
