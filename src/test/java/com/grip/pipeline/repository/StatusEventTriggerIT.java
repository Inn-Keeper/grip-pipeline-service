package com.grip.pipeline.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The status-event trigger from migration 0020: an imported contact's first
 * event is dated by {@code stage_reached_on}, at noon UTC so it stays on the
 * same calendar day across time zones. Plain JDBC; no Spring context needed.
 */
@Testcontainers(disabledWithoutDocker = true)
class StatusEventTriggerIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
      .withInitScript("db/testcontainers-schema.sql");

  @Test
  void insertWithStageDateIsDatedAtNoonUtc() throws Exception {
    try (Connection c = connect(); Statement s = c.createStatement()) {
      UUID id = insert(s, "'2026-09-20'");
      assertThat(firstEventAt(s, id)).isEqualTo(Instant.parse("2026-09-20T12:00:00Z"));
    }
  }

  @Test
  void insertWithoutStageDateIsDatedNow() throws Exception {
    try (Connection c = connect(); Statement s = c.createStatement()) {
      UUID id = insert(s, "null");
      assertThat(secondsFromNow(s, id, "Applied")).isLessThan(60);
    }
  }

  @Test
  void statusUpdateIsDatedNowEvenWithStageDate() throws Exception {
    try (Connection c = connect(); Statement s = c.createStatement()) {
      UUID id = insert(s, "'2026-09-20'");
      s.execute("update contacts set status = 'Interviewing' where id = '" + id + "'");
      assertThat(secondsFromNow(s, id, "Interviewing")).isLessThan(60);
    }
  }

  private static Connection connect() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static UUID insert(Statement s, String stageReachedOn) throws Exception {
    UUID id = UUID.randomUUID();
    s.execute("insert into contacts (id, user_id, name, status, stage_reached_on) values ('"
        + id + "', '" + UUID.randomUUID() + "', 'Acme', 'Applied', " + stageReachedOn + ")");
    return id;
  }

  private static Instant firstEventAt(Statement s, UUID id) throws Exception {
    try (ResultSet rs = s.executeQuery(
        "select created_at from status_events where contact_id = '" + id + "'")) {
      rs.next();
      return rs.getObject(1, OffsetDateTime.class).toInstant();
    }
  }

  private static double secondsFromNow(Statement s, UUID id, String status) throws Exception {
    try (ResultSet rs = s.executeQuery(
        "select abs(extract(epoch from now() - created_at)) from status_events where contact_id = '"
            + id + "' and status = '" + status + "'")) {
      rs.next();
      return rs.getDouble(1);
    }
  }
}
