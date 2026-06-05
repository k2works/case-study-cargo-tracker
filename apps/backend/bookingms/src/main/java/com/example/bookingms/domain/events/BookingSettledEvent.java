package com.example.bookingms.domain.events;

/**
 * 予約「精算済」イベント（US23、IT7 T4.5、cross-service 結果）。
 *
 * <p>Cargo 集約が {@code SETTLED} 状態に遷移したことを表す。Read Model 投影で
 * {@code cargo_summary.booking_status} を更新し、S05 貨物予約一覧画面に SETTLED バッジを表示する。</p>
 *
 * @param bookingId     予約識別子
 * @param bookingStatus 遷移後の業務状態（{@code "SETTLED"}）
 */
public record BookingSettledEvent(
        String bookingId,
        String bookingStatus
) {
}
