package com.example.cargotracker.handling.domain.model.valueobjects;

/**
 * 荷役の種別（domain-model.md「荷役種別ごとの要件」）。<b>handlingms の型</b>。
 *
 * <p><b>要件は型自身が持つ。</b> 呼び出し側に {@code if (type == LOAD)} を書かせると、
 * 種別が増えたときに書き換える場所が散らばる。集約も画面もここに聞く。</p>
 *
 * <p><b>税関は荷役ではない。</b> 通関は {@code CustomsDeclaration}（US29・IT12）が
 * 扱う。ここに {@code CUSTOMS} を足さない。</p>
 *
 * <p><b>列挙名を利用者に見せない。</b> 呼び名は要素表が正典で、
 * {@code HandlingTypeTest#usesTheCanonicalLabel} が読んで突き合わせる。</p>
 */
public enum HandlingType {
    /** 出発港で荷主から受け取った。 */
    RECEIVE("受領"),
    /** 船に積み込んだ。どの船かが要る。 */
    LOAD("積込"),
    /** 船から降ろした。どの船かが要る。 */
    UNLOAD("荷降し"),
    /** 荷受人が引き取った。精算の開始条件（US16・IT10）。 */
    CLAIM("引取");

    private final String label;

    HandlingType(String label) {
        this.label = label;
    }

    /** 利用者に見せる呼び名。 */
    public String label() {
        return label;
    }

    /**
     * 航海番号が要るか。<b>船に紐づく作業だけ</b>。
     *
     * <p>受領は出発港で荷主から受け取る作業、引取は目的港で荷受人へ渡す作業で、
     * どちらも船とは結びつかない。</p>
     */
    public boolean requiresVoyageNumber() {
        return this == LOAD || this == UNLOAD;
    }

    /**
     * 荷受人の確認（署名・確認コード）が要るか。<b>引取だけ</b>。
     *
     * <p>正式な引き渡しを証明する（US16）。ほかの作業は自社の中で完結する。</p>
     */
    public boolean requiresConsigneeConfirmation() {
        return this == CLAIM;
    }

    /**
     * 通関済みであることの検査が要るか。<b>引取だけ</b>。
     *
     * <p><b>これは警告ではなく拒否</b>（不変条件 4）。ほかの要件（予定ルート外）は
     * 警告のうえ記録するが、通関を通っていない貨物は渡せない。</p>
     */
    public boolean requiresCustomsClearance() {
        return this == CLAIM;
    }
}
