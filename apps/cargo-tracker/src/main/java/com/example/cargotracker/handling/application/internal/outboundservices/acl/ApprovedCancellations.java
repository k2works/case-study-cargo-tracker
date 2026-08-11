package com.example.cargotracker.handling.application.internal.outboundservices.acl;

import java.time.Instant;
import java.util.List;

/**
 * 承認済みキャンセルの荷降し手配を読む出力ポート（Handling → Booking の ACL。US30）。
 *
 * <p><strong>「手配した」を現場が読める場所に置く。</strong> 輸送中の予約キャンセルを
 * 追跡管理者が承認すると陸揚げ地が決まるが、IT15 ではその記録が
 * <strong>予約詳細と荷主への通知にしか残らなかった</strong>。
 * 実際に船から降ろす荷役作業員には何も届いていない。
 * <strong>降ろす人が知らないなら、手配したことにならない。</strong>
 *
 * <p><strong>「荷降し手配」と「荷降し」を混ぜない。</strong>
 * {@code HandlingType.UNLOAD}（荷降し）はすでに存在し、それは
 * <strong>現場が実際に降ろした記録</strong>である。本ポートが運ぶ
 * {@link DischargeOrder}（荷降し手配）は<strong>「ここで降ろせ」という指示</strong>であり、
 * まだ降ろしていない。同じ言葉が記録と指示の両方を指すと、
 * 一覧を見た作業員が「もう降ろしたのか」と読む。
 *
 * <p><strong>状態を変えないので、ポートでよい</strong>（ADR-021）。判断基準は
 * 「呼び出し側が戻り値を使っているか」であり、ここは表示に使う。
 * イベントにする必要はない。
 *
 * <p><strong>境界を越える値は本インターフェースの内側に置く</strong>
 * （{@link CargoSnapshots} と同じ形）。実装は Booking 側の
 * {@code infrastructure/acl} が持つ — {@code booking_cancellation} の所有者は
 * Booking であり、Handling のマッパーから引かない（ADR-015）。
 */
public interface ApprovedCancellations {

    /**
     * 承認済みキャンセルの荷降し手配。
     *
     * <p>並びは<strong>承認の古い順</strong>。待たせている手配から捌く。
     *
     * <p><strong>「もう降ろしたか」はここでは絞らない。</strong> 荷役の記録を持つのは
     * Handling であり、Booking のマッパーがそれを引くと BC の越境になる（ADR-015）。
     * <strong>絞るのは呼び出し側の仕事である</strong> — 自分の持ち物で絞れる。
     */
    List<DischargeOrder> findApprovedDischarges();

    /**
     * 荷降しの手配。<strong>すべて素の値である。</strong>
     *
     * @param bookingId         予約 ID。<strong>荷役の記録と突き合わせるための鍵である</strong>
     *                          （画面には出さない）
     * @param trackingNumber    追跡番号。<strong>作業員が手にしているのはこれだけである</strong>
     *                          （予約 ID は紙にもラベルにも無い）。未発行なら {@code null}
     * @param dischargeUnlocode 陸揚げ地（UN/LOCODE）
     * @param dischargeName     陸揚げ地の表示名。<strong>コードだけでは現場に伝わらない</strong>
     * @param decidedAt         承認した日時
     */
    record DischargeOrder(
            String bookingId,
            String trackingNumber,
            String dischargeUnlocode,
            String dischargeName,
            Instant decidedAt) {
    }
}
