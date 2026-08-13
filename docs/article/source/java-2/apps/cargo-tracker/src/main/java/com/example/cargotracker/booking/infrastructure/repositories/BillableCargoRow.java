package com.example.cargotracker.booking.infrastructure.repositories;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 請求対象の 1 行（US21。Billing への ACL が使う）。
 *
 * <p><strong>訂正の申請中と例外の有無は持たない。</strong> どちらも他 BC の持ち物であり、
 * ACL ポートで受け取る（ADR-015。SQL で JOIN しない）。
 */
public class BillableCargoRow {

    private UUID bookingId;
    private String trackingNumber;
    private UUID shipperId;
    private String shipperName;
    private String shipperType;
    private String origin;
    private String destination;
    private String cargoType;
    private BigDecimal weight;
    private int legCount;
    private String bookingStatus;
    private java.time.Instant claimedAt;

    /** 引取が済んだ日時（C1）。<strong>列が無かったころの引取は {@code null}</strong>。 */
    public java.time.Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(java.time.Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public UUID getShipperId() {
        return shipperId;
    }

    public void setShipperId(UUID shipperId) {
        this.shipperId = shipperId;
    }

    public String getShipperName() {
        return shipperName;
    }

    public void setShipperName(String shipperName) {
        this.shipperName = shipperName;
    }

    public String getShipperType() {
        return shipperType;
    }

    public void setShipperType(String shipperType) {
        this.shipperType = shipperType;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public int getLegCount() {
        return legCount;
    }

    public void setLegCount(int legCount) {
        this.legCount = legCount;
    }

    /** 予約の状態。<strong>引取が済んだかは Booking 自身が持つ。</strong> */
    public String getBookingStatus() {
        return bookingStatus;
    }

    public void setBookingStatus(String bookingStatus) {
        this.bookingStatus = bookingStatus;
    }
}
