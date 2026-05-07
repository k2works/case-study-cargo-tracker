package com.example.bookingms.domain.model.valueobjects;

/**
 * 予約ステータス
 */
public enum BookingStatus {
    /** 仮予約 */
    PRELIMINARY,
    /** 経路提案済み */
    ROUTE_PROPOSED,
    /** 確定済み */
    CONFIRMED,
    /** 追跡番号発行済み */
    TRACKING_ISSUED,
    /** 輸送中 */
    IN_TRANSIT,
    /** 配達完了 */
    DELIVERED,
    /** 精算済み */
    SETTLED,
    /** キャンセル */
    CANCELLED
}
