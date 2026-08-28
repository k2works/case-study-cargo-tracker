package com.example.handlingms.infrastructure.repositories;

import java.time.Instant;

/** {@code handling_activity} の 1 行。 */
public class HandlingActivityRecord {

    private Long id;
    private String bookingId;
    private String eventType;
    private Instant eventCompletionTime;
    private String locationUnlocode;
    private String locationName;
    private String voyageNumber;
    private String operatorName;
    private String consigneeConfirmation;
    private boolean offRoute;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getEventCompletionTime() {
        return eventCompletionTime;
    }

    public void setEventCompletionTime(Instant eventCompletionTime) {
        this.eventCompletionTime = eventCompletionTime;
    }

    public String getLocationUnlocode() {
        return locationUnlocode;
    }

    public void setLocationUnlocode(String locationUnlocode) {
        this.locationUnlocode = locationUnlocode;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getConsigneeConfirmation() {
        return consigneeConfirmation;
    }

    public void setConsigneeConfirmation(String consigneeConfirmation) {
        this.consigneeConfirmation = consigneeConfirmation;
    }

    public boolean isOffRoute() {
        return offRoute;
    }

    public void setOffRoute(boolean offRoute) {
        this.offRoute = offRoute;
    }
}
