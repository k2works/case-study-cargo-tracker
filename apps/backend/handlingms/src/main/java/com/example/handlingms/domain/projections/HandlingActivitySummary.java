package com.example.handlingms.domain.projections;

import java.time.LocalDateTime;

/**
 * handling_activity Read Model（US15・US16 / IT5 3.x）。
 *
 * <p>handling_activity テーブルの各カラムに対応する POJO。
 * CargoSnapshot ACL の射影情報（booking_id / origin / destination / cargo_type）は
 * IT5 3.1 で実装するため、本コミットでは null を受け入れる。</p>
 */
public class HandlingActivitySummary {

    private String activityId;
    private String bookingId;
    private String trackingNumber;
    private String originUnlocode;
    private String destinationUnlocode;
    private String cargoType;
    private String handlingType;
    private LocalDateTime occurredAt;
    private LocalDateTime recordedAt;
    private String unlocode;
    private String voyageNumber;
    private String handlerId;
    private boolean unexpected;

    public HandlingActivitySummary() {
        /* MyBatis result mapping。setter で値が設定される。 */
    }

    public String getActivityId() { return activityId; }
    public void setActivityId(String activityId) { this.activityId = activityId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getOriginUnlocode() { return originUnlocode; }
    public void setOriginUnlocode(String originUnlocode) { this.originUnlocode = originUnlocode; }

    public String getDestinationUnlocode() { return destinationUnlocode; }
    public void setDestinationUnlocode(String destinationUnlocode) { this.destinationUnlocode = destinationUnlocode; }

    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    public String getHandlingType() { return handlingType; }
    public void setHandlingType(String handlingType) { this.handlingType = handlingType; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }

    public String getUnlocode() { return unlocode; }
    public void setUnlocode(String unlocode) { this.unlocode = unlocode; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public String getHandlerId() { return handlerId; }
    public void setHandlerId(String handlerId) { this.handlerId = handlerId; }

    public boolean isUnexpected() { return unexpected; }
    public void setUnexpected(boolean unexpected) { this.unexpected = unexpected; }
}
