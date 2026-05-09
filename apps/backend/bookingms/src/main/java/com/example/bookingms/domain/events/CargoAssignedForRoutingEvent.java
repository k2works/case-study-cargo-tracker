package com.example.bookingms.domain.events;

/**
 * 予約引渡しドメインイベント
 * 予約確定後に経路設計者への引渡しを通知するイベント
 */
public record CargoAssignedForRoutingEvent(
        String bookingId,
        String originUnlocode,
        String destinationUnlocode,
        String arrivalDeadline
) {
}
