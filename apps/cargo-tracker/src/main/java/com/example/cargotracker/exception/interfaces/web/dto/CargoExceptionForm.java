package com.example.cargotracker.exception.interfaces.web.dto;

import com.example.cargotracker.exception.domain.model.commands.RecordCargoExceptionCommand;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 貨物例外記録フォーム。
 */
public class CargoExceptionForm {

    @NotBlank(message = "追跡番号は必須です")
    private String trackingNumber;

    @NotNull(message = "例外種別は必須です")
    private ExceptionType exceptionType;

    private String locationCode;

    @NotNull(message = "発生日時は必須です")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime occurredAt;

    private String reason;
    @NotBlank(message = "対応内容は必須です")
    private String resolution;

    public RecordCargoExceptionCommand toCommand() {
        return new RecordCargoExceptionCommand(
                trackingNumber,
                exceptionType,
                locationCode,
                occurredAt,
                reason,
                resolution
        );
    }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public ExceptionType getExceptionType() { return exceptionType; }
    public void setExceptionType(ExceptionType exceptionType) { this.exceptionType = exceptionType; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
