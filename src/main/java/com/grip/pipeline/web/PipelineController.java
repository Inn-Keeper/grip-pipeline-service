package com.grip.pipeline.web;

import com.grip.pipeline.service.DueContact;
import com.grip.pipeline.service.FunnelReport;
import com.grip.pipeline.service.PipelineAnalyticsService;
import com.grip.pipeline.service.VelocityReport;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only hiring-pipeline analytics API.
 *
 * <p>{@code userId} is currently a required request parameter so the service can
 * be demoed without auth wiring. The intended production shape is a JWT filter
 * that derives the user from the Supabase session — at which point the param is
 * dropped in favour of the authenticated principal.
 */
@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {

    private final PipelineAnalyticsService analytics;
    private final Clock clock;

    public PipelineController(PipelineAnalyticsService analytics, Clock clock) {
        this.analytics = analytics;
        this.clock = clock;
    }

    @GetMapping("/funnel")
    public FunnelReport funnel(@RequestParam @NotNull UUID userId) {
        return analytics.funnel(userId);
    }

    @GetMapping("/velocity")
    public VelocityReport velocity(@RequestParam @NotNull UUID userId) {
        return analytics.velocity(userId);
    }

    /**
     * Follow-ups due on or before {@code asOf} (defaults to today in the server
     * clock's zone).
     */
    @GetMapping("/due")
    public List<DueContact> due(
            @RequestParam @NotNull UUID userId,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        LocalDate effectiveDate = asOf != null ? asOf : LocalDate.now(clock);
        return analytics.due(userId, effectiveDate);
    }
}
