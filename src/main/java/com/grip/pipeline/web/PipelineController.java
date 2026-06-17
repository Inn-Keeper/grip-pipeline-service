package com.grip.pipeline.web;

import com.grip.pipeline.service.PipelineAnalyticsService;
import com.grip.pipeline.service.VelocityReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only hiring-pipeline analytics API.
 *
 * <p>The user is derived from the {@code sub} claim of the Supabase JWT, so no
 * endpoint accepts a user id from the caller — a token holder can only read
 * their own pipeline.
 */
@RestController
@RequestMapping("/api/pipeline")
@Tag(
        name = "Pipeline analytics",
        description =
                "Read-only hiring-pipeline analytics over Grip's contacts and status_events. "
                        + "The user is taken from the bearer token; there is no userId parameter.")
@SecurityRequirement(name = "bearer-jwt")
public class PipelineController {

    private final PipelineAnalyticsService analytics;

    public PipelineController(PipelineAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Operation(
            summary = "Stage velocity",
            description =
                    "Average days contacts spend in each stage before moving on, derived from the "
                            + "gaps between consecutive status_events. Contacts with a single "
                            + "recorded event contribute nothing, since no dwell interval exists.")
    @GetMapping("/velocity")
    public VelocityReport velocity(@AuthenticationPrincipal Jwt jwt) {
        return analytics.velocity(currentUserId(jwt));
    }

    /** The Supabase user id is the JWT subject. */
    private static UUID currentUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "JWT subject is not a valid user UUID: " + jwt.getSubject(), e);
        }
    }
}
