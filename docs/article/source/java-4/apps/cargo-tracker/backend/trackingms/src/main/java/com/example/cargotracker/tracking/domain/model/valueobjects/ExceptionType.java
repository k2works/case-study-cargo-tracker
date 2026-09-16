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
     * 追跡管理者が<b>手で起票してよい種別か</b>（US19 §受入基準 1）。
     *
     * <p><b>誤配は荷役が、税関保留は通関が決める。</b> 手で起票できるようにすると、
     * 起きていない誤配を記録でき、経路設計者はそれを組み直そうとする
     * （{@code TransportStatus#isSetByHand} と同じ考え方）。</p>
     *
     * <p>入口を絞るだけで、種別そのものを禁じるのではない——自動起票は通る。</p>
     */
    public boolean reportableByHand() {
        return this != MISROUTE && this != CUSTOMS_HOLD;
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
