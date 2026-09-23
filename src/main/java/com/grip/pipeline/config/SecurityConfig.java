package com.grip.pipeline.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security. Supabase signs session tokens with ES256; this
 * service validates them through the project JWKS and derives the user from the
 * {@code sub} claim, so no endpoint takes a user id from the caller.
 *
 * <p>
 * The OpenAPI docs ({@code /docs}, {@code /v3/api-docs}) and the health probe
 * ({@code /actuator/health}) stay public; everything under {@code /api}
 * requires a valid bearer token, and anything else is denied.
 */
@Configuration
public class SecurityConfig {

  private final List<String> allowedOrigins;

  public SecurityConfig(@Value("${grip.cors.allowed-origins}") String allowedOrigins) {
    // Trimmed because the value arrives as one env var: "a, b" would otherwise
    // register " b", which matches no Origin header and fails silently.
    this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .toList();
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(
                "/docs",
                "/docs/**",
                "/swagger-ui/**",
                "/v3/api-docs",
                "/v3/api-docs/**",
                // The host's health probe carries no token. Safe to expose: the
                // body is a bare status (management.endpoint.health.show-details).
                "/actuator/health")
                .permitAll()
                .requestMatchers("/api/**")
                .authenticated()
                .anyRequest()
                .denyAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // Stateless token API: no CSRF tokens, no HTTP Basic prompt.
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins);
    config.setAllowedMethods(List.of("GET", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }

}
