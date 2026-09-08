package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 例外の種別（domain-model.md の要素表 / UC16）。
 *
 * <p><b>緊急かどうかは種別が答える</b>（不変条件 7）。属性に持たせると、
 * 起票した人が「急ぎではない紛失」を作れてしまう——貨物が見つからないことの
 * 重さは、起票した人の判断で変わるものではない。</p>
 *
 * <p><b>呼び名は型が持つ。</b> 利用者に列挙名（{@code CUSTOMS_HOLD}）を見せない。</p>
 */
public enum ExceptionType {
    /** 予定より遅れている（US19）。 */
    DELAY("遅延"),
    /** 貨物が傷んだ。 */
    DAMAGE("破損"),
    /** 貨物が見つからない。<b>唯一の緊急</b>。 */
    LOSS("紛失"),
    /** 予定ルート外へ運ばれた（US28 が自動起票する）。 */
    MISROUTE("誤配"),
    /** 通関で留め置かれた（UC21 が自動起票する）。 */
    CUSTOMS_HOLD("税関保留");

    private final String label;

    ExceptionType(String label) {
        this.label = label;
    }

    /** 画面に出す呼び名。 */
    public String label() {
        return label;
    }

    /**
     * 緊急か（不変条件 7）。
     *
     * <p><b>紛失だけ。</b> 遅延も破損も業務は続けられるが、見つからない貨物は
     * 探すこと自体が仕事になる。一覧はこれを先頭に並べる。</p>
     */
    public boolean urgent() {
        return this == LOSS;
    }
}
