package com.example.routingms.infrastructure.persistence.record;

import java.time.ZonedDateTime;

/**
 * carrier_movement テーブルの MyBatis マッピング用レコードクラス
 */
public class CarrierMovementRecord {

    private Long id;
    private Long voyageId;
    private String departureLocationUnlocode;
    private String arrivalLocationUnlocode;
    private ZonedDateTime departureDate;
    private ZonedDateTime arrivalDate;
    private int seqNumber;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public CarrierMovementRecord() {
        // Required for MyBatis result mapping
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }

    public String getDepartureLocationUnlocode() { return departureLocationUnlocode; }
    public void setDepartureLocationUnlocode(String departureLocationUnlocode) {
        this.departureLocationUnlocode = departureLocationUnlocode;
    }

    public String getArrivalLocationUnlocode() { return arrivalLocationUnlocode; }
    public void setArrivalLocationUnlocode(String arrivalLocationUnlocode) {
        this.arrivalLocationUnlocode = arrivalLocationUnlocode;
    }

    public ZonedDateTime getDepartureDate() { return departureDate; }
    public void setDepartureDate(ZonedDateTime departureDate) { this.departureDate = departureDate; }

    public ZonedDateTime getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(ZonedDateTime arrivalDate) { this.arrivalDate = arrivalDate; }

    public int getSeqNumber() { return seqNumber; }
    public void setSeqNumber(int seqNumber) { this.seqNumber = seqNumber; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }
}
