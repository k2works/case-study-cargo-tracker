package com.example.trackingms.domain.model;

/**
 * 輸送ステータス（domain-model.md：9 値、レビュー指摘 H1 / IT5）。
 *
 * <p>{@link TrackingActivity} 集約の {@code currentStatus} に使用する。
 * 状態遷移ガード（許可される遷移のみ受理）は IT5 タスク 2.x で
 * {@code TransportStatusTransition} ドメインサービスとして実装する。</p>
 */
public enum TransportStatus {
    /** 未受領（初期状態） */
    NOT_RECEIVED,
    /** 受領済 */
    RECEIVED,
    /** 積込済 */
    LOADED,
    /** 輸送中 */
    IN_TRANSIT,
    /** 荷降し済 */
    UNLOADED,
    /** 引取待ち（最終港到達） */
    AWAITING_CLAIM,
    /** 引取済（配送完了） */
    DELIVERED,
    /** 誤配送 */
    MISROUTED,
    /** 例外発生 */
    EXCEPTION
}
