package com.example.simulationms.domain.model.valueobjects;

/** 1 つの工程の結果。 */
public enum StepOutcome {
    SUCCEEDED("成功"),
    FAILED("失敗");

    private final String label;

    StepOutcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
