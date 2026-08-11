package com.example.cargotracker.estimation.domain.model;

/**
 * ルート候補が 0 件だった理由（US01 の受入基準 5。ADR-023）。
 *
 * <p><strong>「便が無い」と「期限に間に合わない」は別の事態である。</strong>
 * 営業担当者が次に取る行動が違う。
 *
 * <ul>
 *   <li>{@link #NO_VOYAGE} — 別の港を提案するか、他社を当たる</li>
 *   <li>{@link #DEADLINE} — 荷主に期限の延長を相談する</li>
 * </ul>
 *
 * <p>まとめて「候補がありません」と出すと、どちらなのか分からない。
 */
public enum NoCandidateReason {

    /** その区間を走る便が無い。 */
    NO_VOYAGE("この区間を走る便がありません。別の港をご検討ください"),

    /** 便はあるが希望期限に間に合わない。 */
    DEADLINE("希望期限に間に合う便がありません。期限の延長をご検討ください");

    private final String message;

    NoCandidateReason(String message) {
        this.message = message;
    }

    /**
     * 画面に出す案内。
     *
     * <p><strong>気づく手段は次の行動へ繋ぐ。</strong> 「ありません」だけでは、
     * 読んだ人は次に何をすればよいか分からない。
     */
    public String message() {
        return message;
    }
}
