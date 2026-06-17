package com.grip.pipeline.web;

import com.grip.pipeline.service.DueContact;
import com.grip.pipeline.service.FunnelReport;
import com.grip.pipeline.service.PipelineAnalyticsService;
import com.grip.pipeline.service.VelocityReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final Clock clock;

    public PipelineController(PipelineAnalyticsService analytics, Clock clock) {
        this.analytics = analytics;
        this.clock = clock;
    }

    @Operation(
            summary = "Conversion funnel",
            description =
                    "Per-stage reach and conversion rates across the ordered pipeline "
                            + "(Contacted → Applied → Interviewing → Offer), computed from the "
                            + "exact status_events log. Each contact is counted at the furthest "
                            + "stage it reached, so a skipped intermediate stage does not create a "
                            + "survivorship gap.")
    @ApiResponse(
            responseCode = "200",
            description = "Funnel report",
            content =
                    @Content(
                            schema = @Schema(implementation = FunnelReport.class),
                            examples =
                                    @ExampleObject(
                                            value =
                                                    """
                                                    {
                                                      "stages": [
                                                        {"stage": "CONTACTED", "reached": 10, "conversionRate": 1.0000},
                                                        {"stage": "APPLIED", "reached": 7, "conversionRate": 0.7000},
                                                        {"stage": "INTERVIEWING", "reached": 3, "conversionRate": 0.4286},
                                                        {"stage": "OFFER", "reached": 1, "conversionRate": 0.3333}
                                                      ],
                                                      "offers": 1,
                                                      "rejected": 4,
                                                      "totalContacts": 10
                                                    }""")))
    @GetMapping("/funnel")
    public FunnelReport funnel(@AuthenticationPrincipal Jwt jwt) {
        return analytics.funnel(currentUserId(jwt));
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

    @Operation(
            summary = "Due follow-ups",
            description =
                    "Contacts in a non-terminal stage whose next_action_date is on or before "
                            + "asOf. overdueDays is positive when the action date has passed, zero "
                            + "when it is due today.")
    @GetMapping("/due")
    public List<DueContact> due(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(
                            description = "Cut-off date (ISO yyyy-MM-dd); defaults to today",
                            example = "2026-06-17")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        LocalDate effectiveDate = asOf != null ? asOf : LocalDate.now(clock);
        return analytics.due(currentUserId(jwt), effectiveDate);
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
