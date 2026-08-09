package com.example.cargotracker.handling.infrastructure.repositories;

import java.time.Instant;
import java.util.UUID;

/** 荷役作業記録の行。 */
public class HandlingActivityRecord {

    private Long id;
    private UUID bookingId;
    private String eventType;
    private Instant eventCompletionTime;
    private String locationUnlocode;
    private String voyageNumber;
    private String trackingNumber;
    private String claimConfirmationMethod;
    private String claimConfirmationCode;
    private String claimConsigneeName;
    private String note;
    private String operatorName;

    /** 貨物種別（US05）。一覧でのみ読む。読めない場合は {@code null}。 */
    private String cargoType;
    private long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
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

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    /** 読み取った追跡番号（V13）。 */
    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    /** 引取確認の方法（V14。引取以外では null）。 */
    public String getClaimConfirmationMethod() {
        return claimConfirmationMethod;
    }

    public void setClaimConfirmationMethod(String claimConfirmationMethod) {
        this.claimConfirmationMethod = claimConfirmationMethod;
    }

    public String getClaimConfirmationCode() {
        return claimConfirmationCode;
    }

    public void setClaimConfirmationCode(String claimConfirmationCode) {
        this.claimConfirmationCode = claimConfirmationCode;
    }

    public String getClaimConsigneeName() {
        return claimConsigneeName;
    }

    public void setClaimConsigneeName(String claimConsigneeName) {
        this.claimConsigneeName = claimConsigneeName;
    }

    /** 担当者メモ（V15）。 */
    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    /** 取り消された日時（US36）。取り消されていなければ {@code null}。 */
    private java.time.Instant cancelledAt;

    /** 取り消しを承認した追跡管理者（US36）。 */
    private String cancelledBy;

    public java.time.Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(java.time.Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(String cancelledBy) {
        this.cancelledBy = cancelledBy;
    }
}
