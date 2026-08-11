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
 * @param cargoTypeLabel    貨物種別の表示名（US05）。<strong>現物に触る人が特別な
 *                          取り扱いに気づけるようにする。</strong>
 *                          一般貨物・不明なら空文字
 */
public record DischargeOrderView(
        String trackingNumber,
        String dischargeUnlocode,
        String dischargeName,
        Instant decidedAt,
        String cargoTypeLabel) {

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

    /**
     * 画面に出す追跡番号。
     *
     * <p><strong>番号の有無の判定を画面に書かない</strong>（M5）。表示と
     * ボタンの出し分けで別々に見ると、<strong>同じ規則が 2 か所に散る</strong>。
     */
    public String trackingNumberLabel() {
        return registrable() ? trackingNumber : "（未発行）";
    }

    /**
     * 追跡番号が未発行の手配で、現場が次にすること。
     *
     * <p><strong>行き止まりにしない</strong>（M3）。ボタンを出さないだけでは、
     * 見た人は何をすればよいか分からない。
     */
    public String nextActionNote() {
        return registrable() ? "" : "追跡番号が未発行です。追跡管理者に確認してください";
    }

    /**
     * 特別な取り扱いが要る貨物か（US05）。
     *
     * <p><strong>危険物・冷凍だと現場が気づけない状態にしない。</strong>
     * 降ろす準備が変わる。
     */
    public boolean needsSpecialHandling() {
        return cargoTypeLabel != null && !cargoTypeLabel.isBlank();
    }
}
