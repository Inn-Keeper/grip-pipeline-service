package com.grip.pipeline.domain;

import java.util.List;

/**
 * The hiring-pipeline stages, in funnel order. Mirrors the CHECK constraint on
 * {@code contacts.status} in the Grip schema (migration 0001_init.sql).
 *
 * <p>
 * {@link #OFFER} and {@link #REJECTED} are both terminal.
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

  /** DB values of stages that need no follow-up; bound into the due-contact queries. */
  public static final List<String> TERMINAL_DB_VALUES = List.of(OFFER.dbValue, REJECTED.dbValue);

  public static PipelineStage fromDbValue(String value) {
    for (PipelineStage stage : values()) {
      if (stage.dbValue.equals(value)) {
        return stage;
      }
    }
    throw new IllegalArgumentException("Unknown pipeline status: " + value);
  }
}
