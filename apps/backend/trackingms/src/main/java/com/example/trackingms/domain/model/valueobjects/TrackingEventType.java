package com.example.trackingms.domain.model.valueobjects;

/**
 * 荷役イベント種別（Tracking コンテキスト固有）
 */
public enum TrackingEventType {
    /** 受領 */
    RECEIVE,
    /** 積込 */
    LOAD,
    /** 荷降し */
    UNLOAD,
    /** 通関 */
    CUSTOMS,
    /** 引渡し */
    CLAIM
}
