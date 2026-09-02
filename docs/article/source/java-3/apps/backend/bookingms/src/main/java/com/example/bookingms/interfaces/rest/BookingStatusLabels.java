package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.valueobjects.BookingStatus;

/**
 * 予約の状態の表示名。
 *
 * <p><strong>画面に対訳表を置かせない</strong>（[ADR-023] 決定 1 と同じ形）。持たせると、
 * 状態を足したときに画面が列挙の名前をそのまま出す。
 *
 * <p>置き場を interfaces にするのは、これが<strong>画面のための言葉</strong>だからである。
 * ドメインが持つと、業務の語彙と画面の語彙が同じものとして扱われる。
 */
final class BookingStatusLabels {

    private BookingStatusLabels() {
    }

    static String of(BookingStatus status) {
        return switch (status) {
            case PRELIMINARY -> "仮受付";
            case ROUTE_PROPOSED -> "経路提案中";
            case ROUTE_NOTIFIED -> "荷主へ通知済";
            case CONFIRMED -> "確定済";
            case TRACKING_ISSUED -> "追跡番号発行済";
            case IN_TRANSIT -> "輸送中";
            case DELIVERED -> "配送完了";
            case CANCELLED -> "キャンセル";
            case SETTLED -> "精算済";
        };
    }
}
