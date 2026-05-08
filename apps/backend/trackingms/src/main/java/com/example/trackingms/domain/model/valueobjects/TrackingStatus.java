package com.example.trackingms.domain.model.valueobjects;

/**
 * 貨物追跡ステータス（domain-model.md 準拠）
 */
public enum TrackingStatus {
    /** 未受領 */
    NOT_RECEIVED,
    /** 受領済み */
    RECEIVED,
    /** 積込済み */
    LOADED,
    /** 輸送中 */
    ONBOARD_CARRIER,
    /** 荷降し済み */
    UNLOADED,
    /** 引渡し待ち */
    AWAITING_CLAIM,
    /** 引渡し済み */
    CLAIMED,
    /** 例外 */
    EXCEPTION,
    /** 不明 */
    UNKNOWN
}
