package com.grip.pipeline.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.grip.pipeline.service.FunnelReport;
import com.grip.pipeline.service.VelocityReport;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-end test against the real Supabase Postgres, through the full HTTP +
 * JWT security stack. Skipped unless {@code GRIP_DB_URL} is set, so the build
 * stays green without credentials.
 *
 * <p>The JWT secret is pinned here so the test can mint valid HS256 tokens;
 * assertions are data-agnostic (an unknown {@code sub} yields an empty,
 * well-formed report) so they never go brittle as real data changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "GRIP_DB_URL", matches = ".+")
class PipelineEndpointsIT {

    private static final String JWT_SECRET = "test-secret-at-least-32-bytes-long-1234567890";

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.secret-key", () -> JWT_SECRET);
    }

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;

    @Test
    void funnelReturnsWellFormedReportForUnknownUser() {
        ResponseEntity<FunnelReport> response =
                rest.exchange(
                        url("/api/pipeline/funnel"),
                        HttpMethod.GET,
                        bearer(UUID.randomUUID()),
                        FunnelReport.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        FunnelReport body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.totalContacts()).isZero();
        assertThat(body.stages()).hasSize(4);
    }

    @Test
    void velocityReturnsWellFormedReportForUnknownUser() {
        ResponseEntity<VelocityReport> response =
                rest.exchange(
                        url("/api/pipeline/velocity"),
                        HttpMethod.GET,
                        bearer(UUID.randomUUID()),
                        VelocityReport.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().stages()).isEmpty();
    }

    @Test
    void missingTokenYields401() {
        ResponseEntity<String> response =
                rest.getForEntity(url("/api/pipeline/funnel"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void tokenWithNonUuidSubjectYields400() {
        ResponseEntity<String> response =
                rest.exchange(
                        url("/api/pipeline/funnel"),
                        HttpMethod.GET,
                        bearerRaw(signedToken("not-a-uuid")),
                        String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Void> bearer(UUID subject) {
        return bearerRaw(signedToken(subject.toString()));
    }

    private HttpEntity<Void> bearerRaw(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private static String signedToken(String subject) {
        try {
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .subject(subject)
                            .issueTime(Date.from(Instant.now()))
                            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                            .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(JWT_SECRET.getBytes()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint test JWT", e);
        }
    }
}
