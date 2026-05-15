package com.example.cargotracker.bookingms.interfaces.rest.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 貨物予約一覧取得のレスポンス DTO。
 *
 * <p>{@code cargo_summary} Read Model の射影で、一覧画面に必要な
 * 最小限の項目を返す。詳細項目（寸法・危険物・温度条件など）は
 * 予約詳細 API（IT3 以降）で返す。</p>
 */
public record BookingListResponse(
        String bookingId,
        Long shipperId,
        String trackingNumber,
        String cargoType,
        String productName,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        String bookingStatus,
        String routingStatus,
        LocalDateTime createdAt) {
}
