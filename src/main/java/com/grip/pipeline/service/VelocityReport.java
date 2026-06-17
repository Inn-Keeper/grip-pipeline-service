package com.grip.pipeline.service;

import com.grip.pipeline.domain.PipelineStage;
import java.math.BigDecimal;
import java.util.List;

/**
 * Average time contacts spend in each stage before moving on, derived from the
 * gaps between consecutive {@code status_events} for each contact.
 *
 * @param stages per-stage average dwell time
 */
public record VelocityReport(List<StageVelocity> stages) {

    /**
     * @param fromStage   the stage being measured
     * @param toStage     the stage contacts moved to next
     * @param avgDays     mean days between the two transitions, 2-decimal scale
     * @param transitions number of observed transitions averaged
     */
    public record StageVelocity(
            PipelineStage fromStage, PipelineStage toStage, BigDecimal avgDays, long transitions) {}
}
