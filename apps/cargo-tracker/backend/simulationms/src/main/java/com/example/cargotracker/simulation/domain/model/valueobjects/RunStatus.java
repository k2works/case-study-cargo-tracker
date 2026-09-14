package com.example.cargotracker.simulation.domain.model.valueobjects;

/**
 * 実行の状態（US34）。
 *
 * <p><b>終端は 2 つ。</b> 途中で止まっても業務データは取り消さないので、
 * 「失敗」は「何も起きなかった」ではなく「どこまで進んだかが残っている」である。</p>
 */
public enum RunStatus {

    /** 走っている。**同じシナリオはこの状態のものが高々 1 つ**（US33 §5）。 */
    RUNNING("実行中"),
    /** すべての工程が成功した。 */
    SUCCEEDED("成功"),
    /** いずれかの工程が失敗して止まった。**それまでの業務データは残る**。 */
    FAILED("失敗");

    private final String label;

    RunStatus(String label) {
        this.label = label;
    }

    /** 画面に出す呼び名。<b>列挙名を出さない</b>。 */
    public String label() {
        return label;
    }

    /** 終わっているか。終わった実行には工程を足さない。 */
    public boolean isFinished() {
        return this != RUNNING;
    }
}
