package com.example.handlingms.domain.model;

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
     * <p>荷受人の確認が要る（[ADR-023] 決定 4）。これは通関ガード
     * （{@code CustomsDeclaration}・US29・IT9）の<strong>代替</strong>である。ガードが
     * 無いまま引取を通すと「通関前の貨物を引き渡した」記録が残る。IT9 で本物に置き換える。
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
