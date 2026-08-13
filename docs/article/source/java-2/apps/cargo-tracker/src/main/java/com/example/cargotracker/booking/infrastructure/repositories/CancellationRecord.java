package com.example.cargotracker.booking.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code booking_cancellation} の 1 行（US30）。 */
public class CancellationRecord {

    private long id;
    private UUID bookingId;
    private String reason;
    private String requestedBy;
    private Instant requestedAt;
    private String status;
    private BigDecimal feeRate;
    private String dischargeLocationUnlocode;
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

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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

    public BigDecimal getFeeRate() {
        return feeRate;
    }

    public void setFeeRate(BigDecimal feeRate) {
        this.feeRate = feeRate;
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

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
