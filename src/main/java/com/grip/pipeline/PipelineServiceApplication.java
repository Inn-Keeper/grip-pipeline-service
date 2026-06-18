package com.grip.pipeline;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Grip hiring-pipeline analytics + reminders service. */
@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Grip Pipeline Analytics API",
                        version = "0.1.0",
                        description =
                                "Read-only analytics and reminders over Grip's hiring pipeline. "
                                        + "Reads the contacts and status_events tables in Grip's "
                                        + "Supabase Postgres; the React/React Native clients own "
                                        + "all writes."))
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Supabase session JWT. Paste the access_token (ES256).")
public class PipelineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PipelineServiceApplication.class, args);
    }
}
