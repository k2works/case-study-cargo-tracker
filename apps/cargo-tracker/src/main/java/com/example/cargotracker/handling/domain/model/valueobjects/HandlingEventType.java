package com.example.cargotracker.handling.domain.model.valueobjects;

/**
 * 荷役イベント種別。
 */
public enum HandlingEventType {
    /** 積み込み */
    LOAD,
    /** 荷降ろし */
    UNLOAD,
    /** 通関 */
    CUSTOMS,
    /** 積み替え */
    TRANSHIP,
    /** 引取 */
    RECEIVE,
    /** 手動更新 */
    MANUAL_UPDATE
}
