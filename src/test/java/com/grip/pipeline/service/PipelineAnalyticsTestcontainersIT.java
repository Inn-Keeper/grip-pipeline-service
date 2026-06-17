package com.grip.pipeline.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.grip.pipeline.domain.PipelineStage;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end analytics test against a real Postgres in a throwaway container.
 * The schema is derived from Grip's actual migrations
 * ({@code db/testcontainers-schema.sql}); seeding contacts fires the same
 * status-event trigger the production database uses, so the funnel and velocity
 * are computed over genuinely trigger-generated data.
 *
 * <p>Runs with no external credentials, so it executes in CI on every build.
 */
// disabledWithoutDocker skips the whole class (before container startup) when
// no Docker daemon is present, so the build stays green on machines without it
// while still running in CI.
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PipelineAnalyticsTestcontainersIT {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withInitScript("db/testcontainers-schema.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // The analytics tests don't go through HTTP, but the JWT decoder bean
        // still needs a secret to construct at context startup.
        registry.add("spring.security.oauth2.resourceserver.jwt.secret-key", () -> "x".repeat(32));
    }

    @Autowired private PipelineAnalyticsService service;

    @BeforeEach
    void seed() throws Exception {
        try (Connection c =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                Statement s = c.createStatement()) {
            s.execute("truncate status_events, contacts restart identity cascade");

            // Two contacts for USER that advance through stages (trigger writes
            // one status_event per transition). The initial Contacted event is
            // pinned to 2026-01-01 so dwell times are deterministic.
            UUID acme = insertContact(c, USER, "Acme", LocalDate.of(2026, 6, 15));
            pinEvent(c, acme, "Contacted", "2026-01-01 00:00:00+00");
            advance(c, acme, "Applied", "2026-01-02 00:00:00+00"); // +1 day
            advance(c, acme, "Interviewing", "2026-01-06 00:00:00+00"); // +4 days

            UUID globex = insertContact(c, USER, "Globex", null);
            pinEvent(c, globex, "Contacted", "2026-01-01 00:00:00+00");
            advance(c, globex, "Applied", "2026-01-03 00:00:00+00"); // +2 days
            advance(c, globex, "Rejected", "2026-01-05 00:00:00+00");

            // A contact for a different user — must never appear in USER's reports.
            insertContact(c, OTHER_USER, "OtherCorp", LocalDate.of(2026, 6, 15));
        }
    }

    @Test
    void funnelReflectsSeededTransitionsForUserOnly() {
        FunnelReport report = service.funnel(USER);

        assertThat(report.totalContacts()).isEqualTo(2);
        assertThat(report.rejected()).isEqualTo(1);
        assertThat(report.offers()).isZero();
        // Acme + Globex reached Contacted & Applied; only Acme reached Interviewing.
        assertThat(stageReached(report, PipelineStage.CONTACTED)).isEqualTo(2);
        assertThat(stageReached(report, PipelineStage.APPLIED)).isEqualTo(2);
        assertThat(stageReached(report, PipelineStage.INTERVIEWING)).isEqualTo(1);
        assertThat(stageReached(report, PipelineStage.OFFER)).isZero();
    }

    @Test
    void velocityAveragesContactedToAppliedAcrossContacts() {
        VelocityReport report = service.velocity(USER);

        // Contacted→Applied: Acme 1 day, Globex 2 days => avg 1.5.
        VelocityReport.StageVelocity contactedToApplied =
                report.stages().stream()
                        .filter(v -> v.fromStage() == PipelineStage.CONTACTED)
                        .filter(v -> v.toStage() == PipelineStage.APPLIED)
                        .findFirst()
                        .orElseThrow();
        assertThat(contactedToApplied.transitions()).isEqualTo(2);
        assertThat(contactedToApplied.avgDays()).isEqualByComparingTo(BigDecimal.valueOf(1.5));
    }

    @Test
    void dueListsOnlyUsersOwnNonTerminalContacts() {
        // Acme is Interviewing with a past next_action_date → due.
        // Globex is Rejected (terminal) → excluded. OtherCorp belongs to another user.
        var due = service.due(USER, LocalDate.of(2026, 6, 17));

        assertThat(due).hasSize(1);
        assertThat(due.get(0).name()).isEqualTo("Acme");
        assertThat(due.get(0).overdueDays()).isEqualTo(2);
    }

    private static long stageReached(FunnelReport report, PipelineStage stage) {
        return report.stages().stream()
                .filter(s -> s.stage() == stage)
                .findFirst()
                .orElseThrow()
                .reached();
    }

    private static UUID insertContact(Connection c, UUID userId, String name, LocalDate dueDate)
            throws Exception {
        UUID id = UUID.randomUUID();
        try (var ps =
                c.prepareStatement(
                        "insert into contacts (id, user_id, name, status, next_action,"
                                + " next_action_date, created_at) values (?, ?, ?, 'Contacted',"
                                + " 'Follow up', ?, '2026-01-01 00:00:00+00')")) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            ps.setString(3, name);
            if (dueDate != null) {
                ps.setObject(4, dueDate);
            } else {
                ps.setNull(4, java.sql.Types.DATE);
            }
            ps.executeUpdate();
        }
        return id;
    }

    /** Move a contact to a new status, then pin that event's time for deterministic velocity. */
    private static void advance(Connection c, UUID contactId, String status, String at)
            throws Exception {
        try (var ps = c.prepareStatement("update contacts set status = ? where id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, contactId);
            ps.executeUpdate();
        }
        pinEvent(c, contactId, status, at);
    }

    /**
     * Override the timestamp of a trigger-written status_event. The trigger
     * stamps {@code now()}; tests pin it so funnel/velocity math is reproducible.
     */
    private static void pinEvent(Connection c, UUID contactId, String status, String at)
            throws Exception {
        try (var ps =
                c.prepareStatement(
                        "update status_events set created_at = ?::timestamptz "
                                + "where contact_id = ? and status = ?")) {
            ps.setString(1, at);
            ps.setObject(2, contactId);
            ps.setString(3, status);
            ps.executeUpdate();
        }
    }
}
