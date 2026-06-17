package com.grip.pipeline.service;

import com.grip.pipeline.domain.PipelineStage;
import java.math.BigDecimal;
import java.util.List;

/**
 * Conversion funnel across the ordered pipeline stages, plus the terminal
 * outcomes that sit outside the linear sequence.
 *
 * @param stages       per-stage reach + conversion, in funnel order
 * @param offers       contacts that ever reached Offer
 * @param rejected     contacts that ever reached Rejected
 * @param totalContacts distinct contacts with at least one status event
 */
public record FunnelReport(
        List<StageConversion> stages,
        long offers,
        long rejected,
        long totalContacts) {

    /**
     * @param stage          the funnel stage
     * @param reached        distinct contacts that ever reached this stage
     * @param conversionRate fraction of the previous stage that reached this one
     *                       (1.00 for the first stage), 4-decimal scale
     */
    public record StageConversion(PipelineStage stage, long reached, BigDecimal conversionRate) {}
}
