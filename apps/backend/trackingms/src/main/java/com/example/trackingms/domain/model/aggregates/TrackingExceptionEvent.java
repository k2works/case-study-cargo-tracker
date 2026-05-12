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
     * 永続化済み再構成データ
     */
    public record PersistedState(Long id, String responseContent, LocalDate newEstimatedArrival, ExceptionStatus status) {}

    /**
     * 拡張フィールド（DAMAGE/LOST 固有）
     */
    public record ExtendedDetails(String damageDescription, String photoUrl,
                                  String lastKnownLocation, LocalDateTime lastSeenAt) {}

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
    public TrackingExceptionEvent(PersistedState state, ExceptionType exceptionType, LocalDateTime occurredAt,
                                   String locationUnlocode, String reason, boolean escalationFlag) {
        this(exceptionType, occurredAt, locationUnlocode, reason, escalationFlag);
        this.id = state.id();
        this.responseContent = state.responseContent();
        this.newEstimatedArrival = state.newEstimatedArrival();
        this.status = state.status() != null ? state.status() : ExceptionStatus.OPEN;
    }

    /**
     * 新フィールド対応の永続化済み再構成コンストラクタ
     */
    public TrackingExceptionEvent(PersistedState state, ExceptionType exceptionType, LocalDateTime occurredAt,
                                   String locationUnlocode, String reason, boolean escalationFlag,
                                   ExtendedDetails details) {
        this(state, exceptionType, occurredAt, locationUnlocode, reason, escalationFlag);
        this.damageDescription = details.damageDescription();
        this.photoUrl = details.photoUrl();
        this.lastKnownLocation = details.lastKnownLocation();
        this.lastSeenAt = details.lastSeenAt();
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
