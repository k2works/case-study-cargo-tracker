package com.example.simulationms.infrastructure.repositories;

import java.time.Instant;

/** simulation_step_result の 1 行。 */
public class SimulationStepResultRecord {

    private Long id;
    private Long runId;
    private String step;
    private String outcome;
    private Integer elapsedMs;
    private String createdIdentifier;
    private String failureReason;
    private Instant recordedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Integer getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Integer elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getCreatedIdentifier() {
        return createdIdentifier;
    }

    public void setCreatedIdentifier(String createdIdentifier) {
        this.createdIdentifier = createdIdentifier;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
