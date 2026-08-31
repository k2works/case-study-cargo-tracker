package com.example.simulationms.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.Instant;

/** simulation_session の 1 行。 */
public class ContinuousRunSessionRecord {

    private Long id;

    private String sessionId;

    private long seed;

    private int intervalSeconds;

    private int maxConcurrent;

    private BigDecimal exceptionRatio;

    private String status;

    private String startedBy;

    private Instant startedAt;

    private Instant stoppedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public BigDecimal getExceptionRatio() {
        return exceptionRatio;
    }

    public void setExceptionRatio(BigDecimal exceptionRatio) {
        this.exceptionRatio = exceptionRatio;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public void setStartedBy(String startedBy) {
        this.startedBy = startedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
