package com.example.cargotracker.handling.application.internal.queryservices;

import java.time.Instant;

/**
 * 荷降し手配の表示用データ（US30）。
 *
 * <p><strong>「荷降し手配」は指示であって記録ではない。</strong>
 * {@code HandlingType.UNLOAD}（荷降し）は現場が実際に降ろした<strong>記録</strong>である。
 * こちらは「ここで降ろせ」という<strong>指示</strong>であり、まだ降ろしていない。
 * 同じ言葉が両方を指すと、一覧を見た作業員が「もう降ろしたのか」と読む。
 *
 * @param trackingNumber    追跡番号。<strong>作業員が手にしているのはこれだけである</strong>
 * @param dischargeUnlocode 陸揚げ地（UN/LOCODE）
 * @param dischargeName     陸揚げ地の表示名。<strong>コードだけでは現場に伝わらない</strong>
 * @param decidedAt         承認した日時
 */
public record DischargeOrderView(
        String trackingNumber,
        String dischargeUnlocode,
        String dischargeName,
        Instant decidedAt) {

    /**
     * 荷役の登録へ進めるか。
     *
     * <p><strong>追跡番号が無ければ登録画面に渡すものが無い。</strong>
     * 画面の出し分けは本述語をそのまま呼ぶ — 呼び出し側で「番号があれば」と書くと、
     * 規則が 2 か所に散る。
     */
    public boolean registrable() {
        return trackingNumber != null && !trackingNumber.isBlank();
    }
}
