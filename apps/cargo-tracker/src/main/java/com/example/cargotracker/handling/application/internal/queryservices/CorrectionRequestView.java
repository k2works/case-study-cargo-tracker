package com.example.cargotracker.handling.application.internal.queryservices;

import java.time.Instant;

/**
 * 訂正・取り消し申請の 1 行（US36）。
 *
 * <p><strong>誰が何を、どの貨物について申請したのかを 1 行で読めるようにする。</strong>
 * 承認するかどうかは、対象の貨物が分からなければ決められない。
 *
 * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
 * 以前は 11 個の要素が一列に並び、{@code requestedBy} と {@code decidedBy}、
 * {@code requestedAt} と {@code decidedAt} という
 * <strong>「申請」と「決定」の対が隣り合って</strong>いた —
 * 取り違えると、申請者が自分で承認したように見える。
 *
 * <p>画面が呼ぶ名前は委譲するアクセサで残している。
 *
 * @param id             申請 ID
 * @param trackingNumber 対象の追跡番号。<strong>承認者が手にしているのはこれである</strong>
 * @param typeLabel      種別（訂正・取り消し）の表示名
 * @param submission     申請そのもの
 * @param decision       決定
 */
public record CorrectionRequestView(
        long id,
        String trackingNumber,
        String typeLabel,
        Submission submission,
        Decision decision) {

    /**
     * 申請そのもの。
     *
     * @param reason 申請の理由。<strong>これが承認の判断材料である</strong>
     * @param by     申請者。<strong>本人は承認できない</strong>
     * @param at     申請日時
     */
    public record Submission(String reason, String by, Instant at) { }

    /**
     * 決定。
     *
     * @param statusLabel 状態の表示名
     * @param statusBadge 状態のバッジ（正典は {@code CorrectionStatus}）
     * @param by          決定した追跡管理者。未決なら {@code null}
     * @param at          決定日時。未決なら {@code null}
     * @param reason      却下の理由。<strong>申請者が次に何をすればよいかの
     *                    唯一の情報である</strong>。承認・未決なら {@code null}
     */
    public record Decision(
            String statusLabel, String statusBadge, String by, Instant at, String reason) { }

    // --- 画面が呼ぶ名前（委譲するアクセサ）---

    /** @return 申請の理由 */
    public String reason() {
        return submission.reason();
    }

    /** @return 申請者 */
    public String requestedBy() {
        return submission.by();
    }

    /** @return 申請日時 */
    public Instant requestedAt() {
        return submission.at();
    }

    /** @return 状態の表示名 */
    public String statusLabel() {
        return decision.statusLabel();
    }

    /** @return 状態のバッジ */
    public String statusBadge() {
        return decision.statusBadge();
    }

    /** @return 決定した追跡管理者 */
    public String decidedBy() {
        return decision.by();
    }

    /** @return 決定日時 */
    public Instant decidedAt() {
        return decision.at();
    }

    /** @return 却下の理由 */
    public String decisionReason() {
        return decision.reason();
    }


    /** まだ決まっていないか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean isPending() {
        return decision.at() == null;
    }

    /** 却下の理由があるか。**却下されたのに理由が読めないと、次の手が打てない。** */
    public boolean hasDecisionReason() {
        return decision.reason() != null && !decision.reason().isBlank();
    }

    /**
     * 見ている人が申請した本人か（C9）。
     *
     * <p><strong>小規模な拠点では追跡管理者が荷役も兼ねる。</strong> 兼務は例外ではなく
     * 日常であり、自分で申請して自分の画面で承認ボタンを見ることが起きる。
     * ドメインは本人の承認を拒む（US36）が、<strong>画面がボタンを出すと
     * 押した瞬間にエラーになり、なぜ押せないのかはどこにも書いていない</strong>。
     *
     * <p><strong>述語をここに置く。</strong> テンプレートで名前を突き合わせると、
     * 承認の可否がドメインと画面の 2 か所に分かれる。
     */
    public boolean requestedBy(String viewer) {
        return viewer != null && viewer.equals(submission.by());
    }

    /**
     * 見ている人がこの申請を決められるか（C9）。
     *
     * <p><strong>承認・却下のボタンはこの述語だけで出し分ける。</strong>
     * 「承認待ちか」と「本人でないか」を画面で並べると、片方を足し忘れる。
     */
    public boolean decidableBy(String viewer) {
        return isPending() && !requestedBy(viewer);
    }
}
