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
 * @param waitingDays       承認から待っている日数（IT17 の R1）。
 *                          <strong>期限ではなく事実である</strong>
 */
public record DischargeOrderView(
        String trackingNumber,
        String dischargeUnlocode,
        String dischargeName,
        Instant decidedAt,
        String cargoTypeLabel,
        long waitingDays) {

    /** 「注意」に変わる待ち日数。 */
    private static final long WARNING_DAYS = 3;

    /** 「危険」に変わる待ち日数。 */
    private static final long DANGER_DAYS = 7;

    /**
     * 承認から待っている日数（負にはしない）。
     *
     * <p>時刻のずれや手入力で承認が未来になることがある。
     * <strong>負の「待ち日数」は読み手を混乱させる。</strong>
     */
    private long waited() {
        return Math.max(0, waitingDays);
    }

    /**
     * 待ち日数の表示（IT17 の R1）。
     *
     * <p><strong>当日は「本日」と出す。</strong>「0 日」は読み手の手を止める。
     */
    public String waitingLabel() {
        return waited() == 0 ? "本日" : waited() + " 日";
    }

    /**
     * 待ち日数に応じた文字色のクラス。
     *
     * <p><strong>配色（クラス名）だけを期限表示に合わせる。</strong> 閾値の意味は逆である
     * — 期限は<strong>残りが短い</strong>ほど赤く、待ちは<strong>経過が長い</strong>ほど赤い。
     *
     * <p><strong>期限ではなく経過である。</strong> キャンセルの承認は「どこで
     * 降ろすか」を決めるが「いつまでに」は決めていない。無い期限を画面で作ると、
     * <strong>守れなかったときに誰も責任を負えない数字</strong>になる。
     * 貨物が港で滞留するほど保管料と積み替えの手間が増える —
     * <strong>その事実だけを運ぶ。</strong>
     */
    public String waitingUrgencyClass() {
        if (waited() > DANGER_DAYS) {
            return "text-danger";
        }
        return waited() > WARNING_DAYS ? "text-warning" : "text-muted";
    }

    /**
     * 長く待っている手配か。
     *
     * <p><strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> テンプレートで
     * 日数を比べると、閾値が 2 か所に散る。
     */
    public boolean stalled() {
        return waited() > DANGER_DAYS;
    }

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
