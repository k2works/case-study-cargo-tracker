package com.example.cargotracker.exception.domain.model.aggregates;

import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 貨物例外（インシデント）集約ルート。
 * 遅延・破損・紛失などの輸送中に発生した例外事象を記録する。
 * 紛失（LOSS）は緊急フラグが自動設定される。
 */
public class CargoIncident {

    private final ExceptionId id;
    private final String trackingNumber;
    private final ExceptionType exceptionType;
    private final String locationCode;
    private final LocalDateTime occurredAt;
    private final String reason;
    private final boolean urgent;
    private LocalDate estimatedArrivalDate;
    private String resolution;

    private CargoIncident(ExceptionId id, String trackingNumber, ExceptionType exceptionType,
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
     * 貨物例外（インシデント）を記録する。
     */
    public static CargoIncident create(ExceptionId id, String trackingNumber, ExceptionType exceptionType,
                                       String locationCode, LocalDateTime occurredAt, String reason) {
        return new CargoIncident(id, trackingNumber, exceptionType, locationCode, occurredAt, reason);
    }

    /**
     * ストレージから貨物例外（インシデント）を再構成する。
     * urgent フラグは exceptionType から自動算出する。
     */
    public static CargoIncident reconstitute(ExceptionId id, String trackingNumber, ExceptionType exceptionType,
                                             String locationCode, LocalDateTime occurredAt, String reason,
                                             String resolution) {
        CargoIncident incident = new CargoIncident(id, trackingNumber, exceptionType, locationCode, occurredAt, reason);
        incident.resolution = resolution;
        return incident;
    }

    /**
     * 例外に対応内容を記録する。
     */
    public void resolve(String resolution) {
        this.resolution = resolution;
    }

    /**
     * 遅延の場合の新しい到着予定日を設定する。
     */
    public void setEstimatedArrivalDate(LocalDate estimatedArrivalDate) {
        this.estimatedArrivalDate = estimatedArrivalDate;
    }

    public ExceptionId getId() { return id; }
    public String getTrackingNumber() { return trackingNumber; }
    public ExceptionType getExceptionType() { return exceptionType; }
    public String getLocationCode() { return locationCode; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getReason() { return reason; }
    public boolean isUrgent() { return urgent; }
    public LocalDate getEstimatedArrivalDate() { return estimatedArrivalDate; }
    public String getResolution() { return resolution; }
}
