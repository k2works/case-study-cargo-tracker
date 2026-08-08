package com.example.cargotracker.handling.domain.model;

/**
 * 荷役種別。
 *
 * <p>航海番号の要否と場所の照合先は<strong>種別自身が知っている</strong>
 * （{@code domain-model.md}「荷役妥当性検証のデシジョンテーブル」）。
 * 登録処理に対応表を書き写すと、種別が増えたときに片方だけが更新される。
 */
public enum HandlingType {

    /** 受領。出発港での貨物受領。場所は予約の出発地と照合する。 */
    RECEIVE("受領", false),

    /** 積込。航海への積み込み。場所は旅程の積込港と照合する。 */
    LOAD("積込", true),

    /** 荷降し。航海からの荷降ろし。場所は旅程の荷降港と照合する。 */
    UNLOAD("荷降し", true),

    /** 通関。手続きであり、場所は照合しない。 */
    CUSTOMS("通関", false),

    /** 引取。目的港での貨物引取。場所は予約の目的地と照合する（US16 / IT7）。 */
    CLAIM("引取", false);

    private final String displayName;
    private final boolean requiresVoyageNumber;

    HandlingType(String displayName, boolean requiresVoyageNumber) {
        this.displayName = displayName;
        this.requiresVoyageNumber = requiresVoyageNumber;
    }

    /** 画面に出す日本語名（正典は {@code ui_design.md}「HandlingType 表示ラベル定義」）。 */
    public String displayName() {
        return displayName;
    }

    /**
     * 航海番号が必須か。
     *
     * <p>積込・荷降しは「どの便に対する作業か」が分からないと、誤配の判定にも
     * 追跡の表示にも使えない。
     */
    public boolean requiresVoyageNumber() {
        return requiresVoyageNumber;
    }

    /**
     * 場所の食い違いを<strong>誤配として確定する</strong>種別か。
     *
     * <p>積込・荷降しは旅程そのものからの逸脱であり、貨物は予定と違う船・違う港へ
     * 向かう（{@code domain-model.md} 荷役ビジネスルール 1）。受領・引取の
     * 食い違いは<strong>警告に留める</strong>。港の中の別のゲートで受け取るなど、
     * 業務上あり得る差であり、輸送そのものは予定どおり進む。
     */
    public boolean misroutesOnLocationMismatch() {
        return this == LOAD || this == UNLOAD;
    }

    /**
     * 荷受人確認が必須か（US16）。
     *
     * <p><strong>引取だけが必須である。</strong> 引き渡し証明は事故時の唯一の
     * 防御線であり（{@code ui_design.md}）、「渡した」「受け取っていない」の
     * 争いになったとき、確認の記録が無ければ会社が負う。
     *
     * <p>荷役は原則として「予定と違っても記録する」が、
     * <strong>引取だけはその例外である</strong>。証明の無い引き渡しを
     * 「引き渡し済」として残すほうが害が大きい。
     */
    public boolean requiresClaimConfirmation() {
        return this == CLAIM;
    }
}
