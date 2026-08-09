package com.example.cargotracker.handling.infrastructure.repositories;

import java.time.Instant;

/** {@code handling_correction} の 1 行（US36）。 */
public class CorrectionRecord {

    private long id;
    private long handlingActivityId;
    private String requestType;
    private String reason;
    private Instant correctedCompletionTime;
    private String correctedNote;
    private String requestedBy;
    private Instant requestedAt;
    private String status;
    private String decidedBy;
    private Instant decidedAt;
    private String decisionReason;
    private long version;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getHandlingActivityId() {
        return handlingActivityId;
    }

    public void setHandlingActivityId(long handlingActivityId) {
        this.handlingActivityId = handlingActivityId;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCorrectedCompletionTime() {
        return correctedCompletionTime;
    }

    public void setCorrectedCompletionTime(Instant correctedCompletionTime) {
        this.correctedCompletionTime = correctedCompletionTime;
    }

    public String getCorrectedNote() {
        return correctedNote;
    }

    public void setCorrectedNote(String correctedNote) {
        this.correctedNote = correctedNote;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
