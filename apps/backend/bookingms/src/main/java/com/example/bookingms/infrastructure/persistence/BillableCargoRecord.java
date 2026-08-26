package com.example.bookingms.infrastructure.persistence;

import com.example.bookingms.application.port.BillableCargo;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 料金算出の入力の 1 行（US21）。
 *
 * <p>MyBatis が値を差し込むため可変にしている。<strong>組み立て終えたら不変の
 * {@link BillableCargo} に移す</strong>——外へ出すのはそちらである。
 */
public class BillableCargoRecord {

    private String bookingId;
    private String bookingStatus;
    private String shipperId;
    private String shipperName;
    private String shipperType;
    private BigDecimal discountRate;
    private BigDecimal weightKg;
    private String cargoType;
    private String originName;
    private String destinationName;
    private int legCount;
    private Instant claimedAt;
    private Instant misroutedAt;
    private String misroutedLocationUnlocode;
    private String misroutedLocationName;
    private String cancelledAtStatus;
    private Instant cancellationRequestedAt;

    /**
     * 契約の形に移す。
     *
     * <p><strong>誤配もキャンセルも、無ければ項目ごと現れない。</strong>毎回 {@code null} が
     * 出ると、あるかないかを受け取り側が判定しにくい。
     */
    public BillableCargo toBillableCargo() {
        BillableCargo.Misroute misroute = misroutedAt == null ? null
                : new BillableCargo.Misroute(misroutedAt, misroutedLocationUnlocode,
                        misroutedLocationName);
        BillableCargo.Cancellation cancellation = cancelledAtStatus == null ? null
                : new BillableCargo.Cancellation(cancelledAtStatus, cancellationRequestedAt);

        return new BillableCargo(bookingId, bookingStatus, shipperId, shipperName, shipperType,
                discountRate, weightKg, cargoType, originName, destinationName, legCount,
                claimedAt, misroute, cancellation);
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public void setBookingStatus(String bookingStatus) {
        this.bookingStatus = bookingStatus;
    }

    public void setShipperId(String shipperId) {
        this.shipperId = shipperId;
    }

    public void setShipperName(String shipperName) {
        this.shipperName = shipperName;
    }

    public void setShipperType(String shipperType) {
        this.shipperType = shipperType;
    }

    public void setDiscountRate(BigDecimal discountRate) {
        this.discountRate = discountRate;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public void setOriginName(String originName) {
        this.originName = originName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public void setLegCount(int legCount) {
        this.legCount = legCount;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public void setMisroutedAt(Instant misroutedAt) {
        this.misroutedAt = misroutedAt;
    }

    public void setMisroutedLocationUnlocode(String misroutedLocationUnlocode) {
        this.misroutedLocationUnlocode = misroutedLocationUnlocode;
    }

    public void setMisroutedLocationName(String misroutedLocationName) {
        this.misroutedLocationName = misroutedLocationName;
    }

    public void setCancelledAtStatus(String cancelledAtStatus) {
        this.cancelledAtStatus = cancelledAtStatus;
    }

    public void setCancellationRequestedAt(Instant cancellationRequestedAt) {
        this.cancellationRequestedAt = cancellationRequestedAt;
    }
}
