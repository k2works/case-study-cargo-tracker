package com.example.cargotracker.simulation.domain.model.valueobjects;

/** 工程の結果（US34 §受入基準 1）。 */
public enum StepOutcome {

    /** 通った。 */
    SUCCEEDED("成功"),
    /** 通らなかった。**理由（応答コード・メッセージ）を必ず持つ**。 */
    FAILED("失敗");

    private final String label;

    StepOutcome(String label) {
        this.label = label;
    }

    /** 画面に出す呼び名。 */
    public String label() {
        return label;
    }
}
