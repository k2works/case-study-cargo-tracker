package com.example.handlingms.domain.model.valueobjects;

/**
 * 荷役の種別（[ADR-023] 決定 1）。
 *
 * <p><strong>要件は種別そのものが持つ。</strong>呼び出し側に {@code if (type == LOAD)} を
 * 書かせると、種別が増えたときに書き換える場所が散らばり、書き換え忘れた場所だけが
 * 黙って通す。
 */
public enum HandlingType {

    /** 受領。出発港で貨物を預かる。船はまだ関わらない。 */
    RECEIVE(false, false, ExpectedPort.ORIGIN),

    /**
     * 積込。
     *
     * <p>航海番号が要る。<strong>どの船に載せたかが分からないと貨物を追えない。</strong>
     */
    LOAD(true, false, ExpectedPort.ITINERARY_LOAD),

    /** 荷降し。積込と同じく、どの船から降ろしたかが要る。 */
    UNLOAD(true, false, ExpectedPort.ITINERARY_UNLOAD),

    /**
     * 引取。荷受人へ引き渡す。
     *
     * <p>荷受人の確認が要る（[ADR-023] 決定 4）。<strong>これは US16 の受入基準そのもの</strong>
     * であり、通関ガードの代用として入れたのではない——IT7〜IT8 では代用も兼ねていたが、
     * IT9 で本物の通関ガードが入ったあとも残る（[ADR-025] 決定 9）。
     *
     * <p><strong>通関ガードは、この確認の手前に立つ</strong>（US29-3）。順序が入れ替わると、
     * 確認を入れさえすれば通関前でも通ってしまう。
     */
    CLAIM(false, true, ExpectedPort.DESTINATION);

    private final boolean requiresVoyageNumber;
    private final boolean requiresConsigneeConfirmation;
    private final ExpectedPort expectedPort;

    HandlingType(boolean requiresVoyageNumber, boolean requiresConsigneeConfirmation,
            ExpectedPort expectedPort) {
        this.requiresVoyageNumber = requiresVoyageNumber;
        this.requiresConsigneeConfirmation = requiresConsigneeConfirmation;
        this.expectedPort = expectedPort;
    }

    /**
     * 入力された名前から種別を読む。<strong>読み方はここだけが持つ</strong>。
     *
     * <p>入口とユースケースの 2 か所で {@code valueOf} を呼んでいると、
     * <strong>入口が増えた日に、種別の不正が別の見え方をする</strong>——片方は業務の
     * メッセージで断り、もう片方は {@code valueOf} の素の例外で 500 になる。
     *
     * <p>[ADR-023] 決定 1 が「要件は種別そのものが持つ」と決めたのと同じ理由で、
     * 語彙の読み取りも種別に持たせる。
     *
     * @throws IllegalArgumentException 空、または語彙に無い名前
     */
    public static HandlingType parse(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("荷役の種別を選んでください");
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("荷役の種別が不正です: " + name);
        }
    }

    /** 航海番号が必須か。 */
    public boolean requiresVoyageNumber() {
        return requiresVoyageNumber;
    }

    /** 荷受人の確認が必須か（[ADR-023] 決定 4 の代替）。 */
    public boolean requiresConsigneeConfirmation() {
        return requiresConsigneeConfirmation;
    }

    /** 作業場所を照らし合わせる相手。 */
    public ExpectedPort expectedPort() {
        return expectedPort;
    }
}
