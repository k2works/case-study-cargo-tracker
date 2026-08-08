package com.example.cargotracker.routing.application.internal.outboundservices.acl;

/**
 * 航海のスケジュール変更が影響する予約を数える ACL ポート（US25）。
 *
 * <p><strong>運航変更に気づく手段である。</strong> 差分だけを見ても、その便を
 * 使っている予約が何件あるかは分からない。件数が分からないと、経路設計者は
 * <strong>「直しただけで終わり」なのか「連絡が要る仕事が残っている」のか</strong>を
 * 判断できない。
 *
 * <p><strong>境界では素の値だけを受け渡す</strong>（{@code RoutableBookings} と同じ）。
 * ポートは利用する側（Routing）が定義し、アダプタは提供する側（Booking）が実装する。
 */
public interface AffectedBookings {

    /**
     * この航海を確定した経路に含む予約の件数。
     *
     * <p><strong>候補は数えない。</strong> 候補はまだ約束ではなく、算出し直せば変わる。
     * 数えるのは<strong>荷主に日程を伝えた後で変更が起きたもの</strong>である。
     *
     * @param voyageNumber 航海番号
     * @return 件数。0 なら連絡の必要は無い
     */
    int countByVoyageNumber(String voyageNumber);
}
