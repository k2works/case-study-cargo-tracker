package com.example.bookingms.domain.model.valueobjects;

/**
 * 見積の状態（US01）。
 *
 * <p><strong>本 IT で起こす遷移は無い。</strong>作成した見積はそのまま残る。
 * それでも 2 値を宣言するのは正典に合わせるためであり、期限切れの扱い
 * （いつ・誰が {@code EXPIRED} にするか）を決めるのは別のストーリーである
 * ——<strong>書き込む相手がいない状態を先に作らない</strong>。
 */
public enum EstimateStatus {

    /** 作成済み。 */
    CREATED("作成済"),

    /** 期限切れ。**本 IT では誰も遷移させない。** */
    EXPIRED("期限切れ");

    private final String label;

    EstimateStatus(String label) {
        this.label = label;
    }

    /** 画面に出す名前。 */
    public String label() {
        return label;
    }
}
