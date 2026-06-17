package com.grip.pipeline.domain;

import java.util.List;

/**
 * The hiring-pipeline stages, in funnel order. Mirrors the CHECK constraint on
 * {@code contacts.status} in the Grip schema (migration 0001_init.sql).
 *
 * <p>{@link #OFFER} and {@link #REJECTED} are both terminal; only OFFER counts
 * as a positive funnel outcome.
 */
public enum PipelineStage {
    CONTACTED("Contacted"),
    APPLIED("Applied"),
    INTERVIEWING("Interviewing"),
    OFFER("Offer"),
    REJECTED("Rejected");

    private final String dbValue;

    PipelineStage(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    /**
     * The ordered progression a healthy application follows. REJECTED is a
     * terminal off-ramp reachable from any stage and is intentionally excluded
     * from the linear funnel sequence.
     */
    public static final List<PipelineStage> FUNNEL_ORDER =
            List.of(CONTACTED, APPLIED, INTERVIEWING, OFFER);

    public static PipelineStage fromDbValue(String value) {
        for (PipelineStage stage : values()) {
            if (stage.dbValue.equals(value)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unknown pipeline status: " + value);
    }
}
