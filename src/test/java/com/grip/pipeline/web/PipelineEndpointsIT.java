package com.grip.pipeline.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.grip.pipeline.service.FunnelReport;
import com.grip.pipeline.service.VelocityReport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end test against the real Supabase Postgres. Verifies the JPA mappings
 * load and the endpoints respond against the live schema. It is skipped unless
 * {@code GRIP_DB_URL} is set, so {@code ./gradlew build} stays green without
 * credentials.
 *
 * <p>Assertions are data-agnostic on purpose: an unknown {@code userId} must
 * yield an empty-but-well-formed report, so the test never depends on specific
 * seed rows and cannot go brittle as real data changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "GRIP_DB_URL", matches = ".+")
class PipelineEndpointsIT {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;

    @Test
    void funnelReturnsWellFormedReportForUnknownUser() {
        UUID unknownUser = UUID.randomUUID();

        ResponseEntity<FunnelReport> response =
                rest.getForEntity(
                        "http://localhost:" + port + "/api/pipeline/funnel?userId=" + unknownUser,
                        FunnelReport.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        FunnelReport body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.totalContacts()).isZero();
        assertThat(body.stages()).hasSize(4);
    }

    @Test
    void velocityReturnsWellFormedReportForUnknownUser() {
        UUID unknownUser = UUID.randomUUID();

        ResponseEntity<VelocityReport> response =
                rest.getForEntity(
                        "http://localhost:" + port + "/api/pipeline/velocity?userId=" + unknownUser,
                        VelocityReport.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().stages()).isEmpty();
    }

    @Test
    void invalidUserIdYields400() {
        ResponseEntity<String> response =
                rest.getForEntity(
                        "http://localhost:" + port + "/api/pipeline/funnel?userId=not-a-uuid",
                        String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
