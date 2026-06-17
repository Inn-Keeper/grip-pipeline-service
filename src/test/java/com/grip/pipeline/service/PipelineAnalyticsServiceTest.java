package com.grip.pipeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.grip.pipeline.domain.Contact;
import com.grip.pipeline.domain.PipelineStage;
import com.grip.pipeline.domain.StatusEvent;
import com.grip.pipeline.repository.ContactRepository;
import com.grip.pipeline.repository.StatusEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineAnalyticsServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Mock private StatusEventRepository statusEvents;
    @Mock private ContactRepository contacts;
    @InjectMocks private PipelineAnalyticsService service;

    @Test
    void velocityAveragesDwellPerTransition() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // a: Contacted -> Applied after 2 days
        // b: Contacted -> Applied after 4 days  => avg 3 days
        List<StatusEvent> events =
                List.of(
                        Fixtures.statusEvent(a, PipelineStage.CONTACTED, T0),
                        Fixtures.statusEvent(a, PipelineStage.APPLIED, T0.plus(2, ChronoUnit.DAYS)),
                        Fixtures.statusEvent(b, PipelineStage.CONTACTED, T0),
                        Fixtures.statusEvent(b, PipelineStage.APPLIED, T0.plus(4, ChronoUnit.DAYS)));
        when(statusEvents.findByUserIdOrderByCreatedAtAsc(USER)).thenReturn(events);

        VelocityReport report = service.velocity(USER);

        assertThat(report.stages()).hasSize(1);
        VelocityReport.StageVelocity v = report.stages().get(0);
        assertThat(v.fromStage()).isEqualTo(PipelineStage.CONTACTED);
        assertThat(v.toStage()).isEqualTo(PipelineStage.APPLIED);
        assertThat(v.transitions()).isEqualTo(2);
        assertThat(v.avgDays()).isEqualByComparingTo(BigDecimal.valueOf(3.0));
    }

    @Test
    void velocityIgnoresContactsWithSingleEvent() {
        UUID a = UUID.randomUUID();
        when(statusEvents.findByUserIdOrderByCreatedAtAsc(USER))
                .thenReturn(List.of(Fixtures.statusEvent(a, PipelineStage.CONTACTED, T0)));

        assertThat(service.velocity(USER).stages()).isEmpty();
    }

    @Test
    void dueComputesOverdueDaysRelativeToAsOf() {
        LocalDate today = LocalDate.of(2026, 6, 17);
        when(contacts.findDue(USER, today))
                .thenReturn(
                        List.of(
                                Fixtures.dueContact(
                                        "Acme",
                                        PipelineStage.APPLIED,
                                        "Follow up",
                                        LocalDate.of(2026, 6, 15))));

        List<DueContact> due = service.due(USER, today);

        assertThat(due).hasSize(1);
        assertThat(due.get(0).name()).isEqualTo("Acme");
        assertThat(due.get(0).overdueDays()).isEqualTo(2);
    }
}
