package com.grip.pipeline.config;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security. Supabase signs session tokens with HS256 using the
 * project JWT secret; this service validates them and derives the user from the
 * {@code sub} claim, so no endpoint takes a user id from the caller.
 *
 * <p>The OpenAPI docs ({@code /docs}, {@code /v3/api-docs}) stay public so the
 * API can be browsed without a token; everything under {@code /api} requires a
 * valid bearer token.
 */
@Configuration
public class SecurityConfig {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String jwtSecret;
    private final String allowedOrigins;

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.secret-key:}") String jwtSecret,
            @Value("${grip.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.jwtSecret = jwtSecret;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/docs",
                                                "/docs/**",
                                                "/swagger-ui/**",
                                                "/v3/api-docs",
                                                "/v3/api-docs/**")
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
                        ex ->
                                ex.authenticationEntryPoint(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(java.util.List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(java.util.List.of("GET", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * HS256 decoder built from the raw Supabase JWT secret. Supabase's secret is
     * a plain UTF-8 string (not base64), so it is used directly as HMAC key
     * material.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "GRIP_JWT_SECRET is not set. Supply Supabase's JWT secret "
                            + "(Project Settings → API → JWT Secret).");
        }
        SecretKeySpec key = new SecretKeySpec(jwtSecret.getBytes(), HMAC_SHA256);
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
