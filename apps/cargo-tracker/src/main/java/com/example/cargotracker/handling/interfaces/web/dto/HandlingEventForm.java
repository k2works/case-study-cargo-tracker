package com.example.cargotracker.handling.interfaces.web.dto;

import com.example.cargotracker.handling.domain.model.commands.RecordHandlingEventCommand;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;

public class HandlingEventForm {

    @NotBlank(message = "予約 ID は必須です")
    private String bookingId;

    @NotNull(message = "荷役イベント種別は必須です")
    private HandlingEventType eventType;

    @NotBlank(message = "場所コードは必須です")
    private String locationCode;

    @NotNull(message = "完了日時は必須です")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime completionTime;

    private String memo;

    public RecordHandlingEventCommand toCommand() {
        return new RecordHandlingEventCommand(
                UUID.fromString(bookingId),
                eventType,
                locationCode,
                completionTime,
                memo
        );
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public HandlingEventType getEventType() { return eventType; }
    public void setEventType(HandlingEventType eventType) { this.eventType = eventType; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public LocalDateTime getCompletionTime() { return completionTime; }
    public void setCompletionTime(LocalDateTime completionTime) { this.completionTime = completionTime; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
}
