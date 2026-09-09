package com.example.cargotracker.handling.domain.model.valueobjects;

/**
 * 通関状態（domain-model.md の要素表・US29）。
 *
 * <p><b>判定は列挙が答える。</b> 呼び出し側に {@code if (status == CLEARED)} を書かせない。
 * 引取のガードは 2 経路（その場の記録・預かりからの再適用）を通るので、判定を写すと
 * 片方だけ直る形になる（IT11 で 3 件作った欠陥と同じ形）。</p>
 */
public enum CustomsStatus {

    /** 審査中。申告した直後の状態。 */
    PENDING("審査中"),
    /** 通関済。**引取を許すのはここだけ。** */
    CLEARED("通関済"),
    /** 留置。税関保留の例外が自動で起票され、3 営業日を超えると督促の対象になる。 */
    HELD("留置"),
    /** 不可。通らなかった。出し直しはできる。 */
    REJECTED("不可");

    private final String label;

    CustomsStatus(String label) {
        this.label = label;
    }

    /** 利用者に見せる呼び名（要素表が正典）。列挙名をそのまま出さない。 */
    public String label() {
        return label;
    }

    /** 引取（CLAIM）を許すか（US29 §受入基準 3）。 */
    public boolean allowsClaim() {
        return this == CLEARED;
    }

    /**
     * まだ決着していないか（不変条件 3）。
     *
     * <p>未決着の申告は貨物あたり高々 1 件である。{@code REJECTED} の後は出し直せて、
     * {@code CLEARED} の後は断る——どちらも決着しているが扱いが違うので、
     * この述語だけでは決められない。決めるのはアプリケーション層である。</p>
     */
    public boolean unsettled() {
        return this == PENDING || this == HELD;
    }
}
