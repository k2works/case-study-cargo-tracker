package com.example.trackingms.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 追跡例外エンティティ（US19 / US20 / domain-model.md）。
 *
 * <p>{@link TrackingActivity} 集約内のエンティティ。集約スコープの {@link TrackingExceptionId} で識別され、
 * 状態遷移（{@link ResponseStatus}）と解決日時を保持する。LOSS 種別の場合は集約コンストラクタで
 * {@code escalated = true} を自動設定し、{@code TrackingExceptionEscalatedEvent} の発行契機となる
 * （US20 受入基準 3）。</p>
 *
 * <p><strong>不変条件</strong></p>
 * <ul>
 *   <li>{@code type = LOSS} のとき {@code escalated = true}</li>
 *   <li>{@code responseStatus} の遷移は {@link ResponseStatus#canTransitionTo} が許可するもののみ</li>
 *   <li>{@code resolution} は {@code responseStatus = RESOLVED} への遷移時のみ必須（空文字拒否）</li>
 *   <li>{@code resolvedAt} は {@code RESOLVED} 遷移時に集約側で {@code LocalDateTime.now()} で自動設定</li>
 * </ul>
 *
 * <p>SonarQube 警告抑制: ドメインモデル M5（domain-model.md / user_story.md US19/US20）の業務用語
 * 「追跡例外」をクラス名にそのまま使用するため、命名規約「Exception で終わるクラスは Throwable 継承」
 * のチェック（{@code java:S2166}）を抑制する。本クラスは Throwable ではなくエンティティであり、
 * ユビキタス言語との整合を命名規約より優先する。</p>
 */
@SuppressWarnings("java:S2166")
public final class TrackingException {

    private final TrackingExceptionId exceptionId;
    private final ExceptionType type;
    private final LocalDateTime occurredAt;
    private final String occurredUnlocode;
    private final String description;
    private ResponseStatus responseStatus;
    private String resolution;
    private LocalDateTime resolvedAt;
    private final boolean escalated;

    public TrackingException(
            TrackingExceptionId exceptionId,
            ExceptionType type,
            LocalDateTime occurredAt,
            String occurredUnlocode,
            String description
    ) {
        this.exceptionId = Objects.requireNonNull(exceptionId, "exceptionId");
        this.type = Objects.requireNonNull(type, "type");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.occurredUnlocode = occurredUnlocode;
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description は必須です");
        }
        this.description = description;
        this.responseStatus = ResponseStatus.REPORTED;
        this.resolution = null;
        this.resolvedAt = null;
        this.escalated = type.isEscalationRequired();
    }

    /**
     * イベントソーシング再生用のコンストラクタ。
     * 既存例外の状態を完全に復元するために使う（resolution / resolvedAt / responseStatus を含む）。
     *
     * <p>SonarQube 警告抑制（{@code java:S107}）: イベント再生時に状態を完全復元するため、可変フィールド
     * （responseStatus / resolution / resolvedAt）も含めて 8 引数受け取る必要がある。IT7 リファクタで
     * 引数オブジェクト（{@code TrackingExceptionSnapshot}）への移行を予定（IT6 review M2 連携）。</p>
     */
    @SuppressWarnings("java:S107")
    public static TrackingException replay(
            TrackingExceptionId exceptionId,
            ExceptionType type,
            LocalDateTime occurredAt,
            String occurredUnlocode,
            String description,
            ResponseStatus responseStatus,
            String resolution,
            LocalDateTime resolvedAt
    ) {
        TrackingException te = new TrackingException(exceptionId, type, occurredAt,
                occurredUnlocode, description);
        te.responseStatus = Objects.requireNonNull(responseStatus, "responseStatus");
        te.resolution = resolution;
        te.resolvedAt = resolvedAt;
        return te;
    }

    /**
     * 解決済み状態への遷移（US19 受入基準 5）。
     *
     * @param resolution 対応内容（補償方針・代替ルート等）。空文字は拒否
     * @param now        解決日時（集約側で {@code LocalDateTime.now()} を渡す）
     */
    public void resolve(String resolution, LocalDateTime now) {
        if (!responseStatus.canTransitionTo(ResponseStatus.RESOLVED)) {
            throw new IllegalStateException(
                    "RESOLVED への遷移は許可されていません: 現在 " + responseStatus);
        }
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("resolution は RESOLVED 遷移時に必須です");
        }
        this.responseStatus = ResponseStatus.RESOLVED;
        this.resolution = resolution;
        this.resolvedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * 対応中状態への遷移（US19 受入基準 4）。対応内容を入力中。
     */
    public void startResponding() {
        if (!responseStatus.canTransitionTo(ResponseStatus.RESPONDING)) {
            throw new IllegalStateException(
                    "RESPONDING への遷移は許可されていません: 現在 " + responseStatus);
        }
        this.responseStatus = ResponseStatus.RESPONDING;
    }

    public TrackingExceptionId getExceptionId() {
        return exceptionId;
    }

    public ExceptionType getType() {
        return type;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getOccurredUnlocode() {
        return occurredUnlocode;
    }

    public String getDescription() {
        return description;
    }

    public ResponseStatus getResponseStatus() {
        return responseStatus;
    }

    public String getResolution() {
        return resolution;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public boolean isEscalated() {
        return escalated;
    }

    /**
     * Axon の Aggregate Test Fixture が「working aggregate 状態」と「event store からの sourcing 後の状態」を
     * 比較するため、値ベースの equals/hashCode が必要。可変フィールドを含めた全フィールドで比較する。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackingException other)) return false;
        return escalated == other.escalated
                && Objects.equals(exceptionId, other.exceptionId)
                && type == other.type
                && Objects.equals(occurredAt, other.occurredAt)
                && Objects.equals(occurredUnlocode, other.occurredUnlocode)
                && Objects.equals(description, other.description)
                && responseStatus == other.responseStatus
                && Objects.equals(resolution, other.resolution)
                && Objects.equals(resolvedAt, other.resolvedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exceptionId, type, occurredAt, occurredUnlocode, description,
                responseStatus, resolution, resolvedAt, escalated);
    }
}
