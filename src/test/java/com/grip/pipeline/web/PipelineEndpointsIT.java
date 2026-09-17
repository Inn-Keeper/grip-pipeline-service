package com.grip.pipeline.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.grip.pipeline.domain.PipelineStage;
import com.grip.pipeline.service.VelocityReport;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full HTTP + JWT security stack against a throwaway Postgres.
 *
 * <p>
 * Tokens are ES256-signed with a key generated here and published through a
 * local JWKS endpoint, so requests go through the same JWKS + ES256 validation
 * as Supabase tokens in production, without secrets. Skipped automatically
 * when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PipelineEndpointsIT {

  private static final UUID OWNER = UUID.randomUUID();
  private static final ECKey SIGNING_KEY = generateKey();
  private static final HttpServer JWKS = serveJwks(SIGNING_KEY);

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
      .withInitScript("db/testcontainers-schema.sql");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks.json");
  }

  @LocalServerPort
  private int port;
  @Autowired
  private TestRestTemplate rest;

  @BeforeAll
  static void seedOneTransitionForOwner() throws Exception {
    try (Connection c = DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      UUID contact = UUID.randomUUID();
      try (var insert = c.prepareStatement(
          "insert into contacts (id, user_id, name) values (?, ?, 'Acme')")) {
        insert.setObject(1, contact);
        insert.setObject(2, OWNER);
        insert.executeUpdate();
      }
      // The status trigger records Contacted on insert and Applied on this update.
      try (var advance = c.prepareStatement("update contacts set status = 'Applied' where id = ?")) {
        advance.setObject(1, contact);
        advance.executeUpdate();
      }
    }
  }

  @AfterAll
  static void stopJwks() {
    JWKS.stop(0);
  }

  @Test
  void velocityIsScopedToTheTokenSubject() {
    VelocityReport owner = getVelocity(bearer(OWNER.toString(), SIGNING_KEY)).getBody();
    assertThat(owner).isNotNull();
    assertThat(owner.stages()).hasSize(1);
    assertThat(owner.stages().get(0).fromStage()).isEqualTo(PipelineStage.CONTACTED);
    assertThat(owner.stages().get(0).toStage()).isEqualTo(PipelineStage.APPLIED);

    VelocityReport stranger = getVelocity(bearer(UUID.randomUUID().toString(), SIGNING_KEY)).getBody();
    assertThat(stranger).isNotNull();
    assertThat(stranger.stages()).isEmpty();
  }

  @Test
  void missingTokenYields401() {
    assertThat(status(HttpMethod.GET, "/api/pipeline/velocity", new HttpHeaders()))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokenSignedByAnotherKeyYields401() {
    HttpHeaders forged = bearer(OWNER.toString(), generateKey());
    assertThat(status(HttpMethod.GET, "/api/pipeline/velocity", forged))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokenWithNonUuidSubjectYields400() {
    HttpHeaders headers = bearer("not-a-uuid", SIGNING_KEY);
    assertThat(status(HttpMethod.GET, "/api/pipeline/velocity", headers))
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void unknownApiPathYields404() {
    HttpHeaders headers = bearer(OWNER.toString(), SIGNING_KEY);
    assertThat(status(HttpMethod.GET, "/api/pipeline/funnel", headers))
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unsupportedMethodYields405WithAllowHeader() {
    ResponseEntity<String> response = rest.exchange(
        url("/api/pipeline/velocity"),
        HttpMethod.POST,
        new HttpEntity<>(bearer(OWNER.toString(), SIGNING_KEY)),
        String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
  }

  @Test
  void unacceptableMediaTypeYields406() {
    HttpHeaders headers = bearer(OWNER.toString(), SIGNING_KEY);
    headers.set(HttpHeaders.ACCEPT, "text/csv");
    assertThat(status(HttpMethod.GET, "/api/pipeline/velocity", headers))
        .isEqualTo(HttpStatus.NOT_ACCEPTABLE);
  }

  private ResponseEntity<VelocityReport> getVelocity(HttpHeaders headers) {
    ResponseEntity<VelocityReport> response = rest.exchange(
        url("/api/pipeline/velocity"), HttpMethod.GET, new HttpEntity<>(headers), VelocityReport.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response;
  }

  private HttpStatus status(HttpMethod method, String path, HttpHeaders headers) {
    return HttpStatus.valueOf(rest.exchange(url(path), method, new HttpEntity<>(headers), String.class)
        .getStatusCode()
        .value());
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private static HttpHeaders bearer(String subject, ECKey key) {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject(subject)
        .audience("authenticated")
        .issueTime(Date.from(Instant.now()))
        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
        .build();
    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(), claims);
    try {
      jwt.sign(new ECDSASigner(key));
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to sign test JWT", e);
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(jwt.serialize());
    return headers;
  }

  private static ECKey generateKey() {
    try {
      // Same key id for every key, so a forged token cannot be told apart by kid alone.
      return new ECKeyGenerator(Curve.P_256).keyID("test-key").generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to generate test signing key", e);
    }
  }

  private static HttpServer serveJwks(ECKey key) {
    byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/jwks.json", exchange -> {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(body);
        }
      });
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
