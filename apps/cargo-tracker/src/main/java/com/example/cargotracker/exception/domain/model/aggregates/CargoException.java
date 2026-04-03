package com.example.cargotracker.exception.domain.model.aggregates;

import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;

import java.time.LocalDateTime;

/**
 * 貨物例外集約ルート。
 * 遅延・破損・紛失などの輸送中に発生した例外事象を記録する。
 * 紛失（LOSS）は緊急フラグが自動設定される。
 */
public class CargoException {

    private final ExceptionId id;
    private final String trackingNumber;
    private final ExceptionType exceptionType;
    private final String locationCode;
    private final LocalDateTime occurredAt;
    private final String reason;
    private final boolean urgent;
    private String resolution;

    private CargoException(ExceptionId id, String trackingNumber, ExceptionType exceptionType,
                           String locationCode, LocalDateTime occurredAt, String reason) {
        if (id == null) throw new IllegalArgumentException("例外 ID は null にできません");
        if (trackingNumber == null || trackingNumber.isBlank()) throw new IllegalArgumentException("追跡番号は null または空にできません");
        if (exceptionType == null) throw new IllegalArgumentException("例外種別は null にできません");
        if (occurredAt == null) throw new IllegalArgumentException("発生日時は null にできません");
        this.id = id;
        this.trackingNumber = trackingNumber;
        this.exceptionType = exceptionType;
        this.locationCode = locationCode;
        this.occurredAt = occurredAt;
        this.reason = reason;
        this.urgent = exceptionType.isUrgent();
    }

    /**
     * 貨物例外を記録する。
     */
    public static CargoException record(ExceptionId id, String trackingNumber, ExceptionType exceptionType,
                                        String locationCode, LocalDateTime occurredAt, String reason) {
        return new CargoException(id, trackingNumber, exceptionType, locationCode, occurredAt, reason);
    }

    /**
     * ストレージから貨物例外を再構成する。
     */
    public static CargoException reconstitute(ExceptionId id, String trackingNumber, ExceptionType exceptionType,
                                              String locationCode, LocalDateTime occurredAt, String reason,
                                              boolean urgent, String resolution) {
        CargoException e = new CargoException(id, trackingNumber, exceptionType, locationCode, occurredAt, reason);
        e.resolution = resolution;
        return e;
    }

    /**
     * 例外に対応内容を記録する。
     */
    public void resolve(String resolution) {
        this.resolution = resolution;
    }

    public ExceptionId getId() { return id; }
    public String getTrackingNumber() { return trackingNumber; }
    public ExceptionType getExceptionType() { return exceptionType; }
    public String getLocationCode() { return locationCode; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getReason() { return reason; }
    public boolean isUrgent() { return urgent; }
    public String getResolution() { return resolution; }
}
