package com.example.cargotracker.shared.domain.event;

import java.time.Instant;
import java.util.List;

/**
 * 航海のスケジュールが変わった（US25 / IT12 持ち越し C3）。
 *
 * <p>Routing Context が発行し、Booking Context が<strong>区間の「いまの日程」を
 * 自分のテーブルに写す</strong>。写しがあれば、予約詳細は
 * {@code voyage} / {@code carrier_movement} を JOIN せずに
 * 「日程が変わりました」の印を出せる（ADR-015 の許容リストから 2 行を返した）。
 *
 * <p><strong>Routing から他 BC を呼ばない</strong>（ADR-012）。運ぶのは起きた事実であり
 * 命令ではない。どう解釈するかは購読側が決める（ADR-009）。
 * <strong>経路の作り直しはしない</strong> — 利用者の知らないうちに経路が変わる
 * （再設計は US28 の領分である）。
 *
 * @param voyageNumber 航海番号
 * @param movements    変更後の区間。<strong>変わった区間だけでなく全区間を運ぶ</strong> —
 *                     購読側が「変わらなかった区間」も同じ値で写せば、
 *                     写しと実際がずれない
 * @param rescheduledAt 変更日時
 */
public record VoyageRescheduledEvent(
        String voyageNumber,
        List<MovementSchedule> movements,
        Instant rescheduledAt) {

    public VoyageRescheduledEvent {
        movements = List.copyOf(movements);
    }

    /**
     * 区間 1 本の日程。
     *
     * <p><strong>運ぶのは素の値だけである</strong>（ADR-005）。{@code CarrierMovement} を
     * 渡すと Booking が Routing のドメインを参照することになる。
     *
     * @param departureUnlocode 出発港の UN/LOCODE
     * @param arrivalUnlocode   到着港の UN/LOCODE
     * @param departureTime     出発日時
     * @param arrivalTime       到着日時
     */
    public record MovementSchedule(
            String departureUnlocode,
            String arrivalUnlocode,
            Instant departureTime,
            Instant arrivalTime) {
    }
}
