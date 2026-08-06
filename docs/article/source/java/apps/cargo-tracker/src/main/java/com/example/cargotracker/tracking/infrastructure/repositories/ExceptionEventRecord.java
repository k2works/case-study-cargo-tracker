package com.example.cargotracker.tracking.infrastructure.repositories;

import java.time.LocalDateTime;

public class ExceptionEventRecord {

    private String trackingNumber;
    private String exceptionType;
    private String locationUnlocode;
    private LocalDateTime occurrenceTime;
    private String reason;
    private String responseNote;
    private boolean escalationFlag;

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String getLocationUnlocode() { return locationUnlocode; }
    public void setLocationUnlocode(String locationUnlocode) { this.locationUnlocode = locationUnlocode; }

    public LocalDateTime getOccurrenceTime() { return occurrenceTime; }
    public void setOccurrenceTime(LocalDateTime occurrenceTime) { this.occurrenceTime = occurrenceTime; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getResponseNote() { return responseNote; }
    public void setResponseNote(String responseNote) { this.responseNote = responseNote; }

    public boolean isEscalationFlag() { return escalationFlag; }
    public void setEscalationFlag(boolean escalationFlag) { this.escalationFlag = escalationFlag; }
}
