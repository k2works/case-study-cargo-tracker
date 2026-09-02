package com.example.simulationms.domain.model.valueobjects;

/** 実行の状態。 */
public enum RunStatus {
    /** 実行中。同じシナリオの二重実行を断る根拠になる。 */
    RUNNING("実行中"),
    /** シナリオの全工程を終えた。 */
    COMPLETED("完了"),
    /**
     * どこかの工程で失敗した。
     *
     * <p><strong>それまでの業務データは残っている。</strong>巻き戻さないのが
     * [ADR-030] 決定 5 である。
     */
    FAILED("失敗");

    private final String label;

    RunStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
