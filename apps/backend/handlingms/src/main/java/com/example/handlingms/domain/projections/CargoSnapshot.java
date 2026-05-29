package com.example.handlingms.domain.projections;

/**
 * Booking Context の Cargo を ACL 経由で射影した最小情報（domain-model.md H5 / IT5 3.1）。
 *
 * <p>handlingms は bookingms の {@code cargo_summary} を直接 JOIN しない設計のため、
 * shared モジュールのクロスサービスイベント（{@code TrackingIssuanceRequestedEvent} と
 * {@code CargoTrackedEvent}）を購読して必要最小情報のみを {@code cargo_snapshot} に保存する。</p>
 *
 * <p>{@code trackingNumber} は {@code CargoTrackedEvent} 受信時に確定する（採番完了通知）。
 * それまでは null。</p>
 */
public class CargoSnapshot {

    private String bookingId;
    private String trackingNumber;
    private String originUnlocode;
    private String destinationUnlocode;
    private String cargoType;

    public CargoSnapshot() {
        /* MyBatis result mapping。setter で値が設定される。 */
    }

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
}
