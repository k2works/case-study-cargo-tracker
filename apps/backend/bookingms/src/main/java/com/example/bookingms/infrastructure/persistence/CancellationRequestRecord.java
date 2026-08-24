package com.example.bookingms.infrastructure.persistence;

import java.time.Instant;

/** {@code cancellation_request} の 1 行。 */
public class CancellationRequestRecord {

    private Long id;
    private Long cargoId;
    private String reason;
    private String status;
    private String requestedBy;
    private Instant requestedAt;
    private String bookingStatusAtRequest;
    private String dischargeLocationUnlocode;
    private String decidedBy;
    private Instant decidedAt;
    private String decisionReason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCargoId() {
        return cargoId;
    }

    public void setCargoId(Long cargoId) {
        this.cargoId = cargoId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getBookingStatusAtRequest() {
        return bookingStatusAtRequest;
    }

    public void setBookingStatusAtRequest(String bookingStatusAtRequest) {
        this.bookingStatusAtRequest = bookingStatusAtRequest;
    }

    public String getDischargeLocationUnlocode() {
        return dischargeLocationUnlocode;
    }

    public void setDischargeLocationUnlocode(String dischargeLocationUnlocode) {
        this.dischargeLocationUnlocode = dischargeLocationUnlocode;
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
}
