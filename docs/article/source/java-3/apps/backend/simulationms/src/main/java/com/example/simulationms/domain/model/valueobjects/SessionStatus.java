package com.example.simulationms.domain.model.valueobjects;

/**
 * 継続実行の状態（[ADR-031] 決定 4）。
 *
 * <p><strong>「止めた」と「止まった」を分ける。</strong>分けないと、進行中の実行が
 * 残っているのに停止済みと表示され、統計が確定していない状態で読まれる。
 */
public enum SessionStatus {
    /** 実行中。上限に達していなければ新しい実行を始める。 */
    RUNNING("実行中"),
    /** 停止処理中。新規の開始は止まっているが、進行中の実行が残っている。 */
    STOPPING("停止処理中"),
    /** 停止済み。進行中の実行も尽きた。 */
    STOPPED("停止済み");

    private final String label;

    SessionStatus(String label) {
        this.label = label;
    }

    /** 画面に出す名前。**画面が対訳表を持たない**——足したときに画面だけが古くなる。 */
    public String label() {
        return label;
    }
}
