package com.example.trackingms.domain.model.aggregates;

import com.example.trackingms.domain.model.valueobjects.ExceptionStatus;
import com.example.trackingms.domain.model.valueobjects.ExceptionType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 追跡例外イベント
 */
public class TrackingExceptionEvent {

    private Long id;
    private final ExceptionType exceptionType;
    private final LocalDateTime occurredAt;
    private final String locationUnlocode;
    private final String reason;
    private final boolean escalationFlag;
    private String responseContent;
    private LocalDate newEstimatedArrival;
    private ExceptionStatus status;
    private boolean responded = false;
    // DAMAGE 固有フィールド
    private String damageDescription;
    private String photoUrl;
    // LOST 固有フィールド
    private String lastKnownLocation;
    private LocalDateTime lastSeenAt;

    /**
     * 新規例外イベント作成コンストラクタ
     */
    public TrackingExceptionEvent(ExceptionType exceptionType, LocalDateTime occurredAt,
                                   String locationUnlocode, String reason, boolean escalationFlag) {
        Objects.requireNonNull(exceptionType, "exceptionType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.exceptionType = exceptionType;
        this.occurredAt = occurredAt;
        this.locationUnlocode = locationUnlocode;
        this.reason = reason;
        this.escalationFlag = escalationFlag;
        this.status = ExceptionStatus.OPEN;
    }

    /**
     * 永続化済み再構成コンストラクタ
     */
    public TrackingExceptionEvent(Long id, ExceptionType exceptionType, LocalDateTime occurredAt,
                                   String locationUnlocode, String reason, boolean escalationFlag,
                                   String responseContent, LocalDate newEstimatedArrival, ExceptionStatus status) {
        this(exceptionType, occurredAt, locationUnlocode, reason, escalationFlag);
        this.id = id;
        this.responseContent = responseContent;
        this.newEstimatedArrival = newEstimatedArrival;
        this.status = status != null ? status : ExceptionStatus.OPEN;
    }

    /**
     * 新フィールド対応の永続化済み再構成コンストラクタ
     */
    public TrackingExceptionEvent(Long id, ExceptionType exceptionType, LocalDateTime occurredAt,
                                   String locationUnlocode, String reason, boolean escalationFlag,
                                   String responseContent, LocalDate newEstimatedArrival, ExceptionStatus status,
                                   String damageDescription, String photoUrl,
                                   String lastKnownLocation, LocalDateTime lastSeenAt) {
        this(id, exceptionType, occurredAt, locationUnlocode, reason, escalationFlag,
                responseContent, newEstimatedArrival, status);
        this.damageDescription = damageDescription;
        this.photoUrl = photoUrl;
        this.lastKnownLocation = lastKnownLocation;
        this.lastSeenAt = lastSeenAt;
    }

    /**
     * 対応内容を更新する
     */
    public void respond(String responseContent, LocalDate newEstimatedArrival) {
        if (responseContent == null || responseContent.isBlank()) {
            throw new IllegalArgumentException("responseContent must not be blank");
        }
        this.responseContent = responseContent;
        this.newEstimatedArrival = newEstimatedArrival;
        this.status = ExceptionStatus.IN_PROGRESS;
        this.responded = true;
    }

    public void recordDamageDetails(String damageDescription, String photoUrl) {
        this.damageDescription = damageDescription;
        this.photoUrl = photoUrl;
    }

    public void recordLostDetails(String lastKnownLocation, LocalDateTime lastSeenAt) {
        this.lastKnownLocation = lastKnownLocation;
        this.lastSeenAt = lastSeenAt;
    }

    public boolean hasBeenResponded() { return responded; }

    public Long getId() { return id; }
    public ExceptionType getExceptionType() { return exceptionType; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getLocationUnlocode() { return locationUnlocode; }
    public String getReason() { return reason; }
    public boolean isEscalationFlag() { return escalationFlag; }
    public String getResponseContent() { return responseContent; }
    public LocalDate getNewEstimatedArrival() { return newEstimatedArrival; }
    public ExceptionStatus getStatus() { return status; }
    public String getDamageDescription() { return damageDescription; }
    public String getPhotoUrl() { return photoUrl; }
    public String getLastKnownLocation() { return lastKnownLocation; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
}
