package com.example.bookingms.domain.model.valueobjects;

/**
 * キャンセル申請の状態（US30・UC22）。
 *
 * <p><strong>予約の状態とは別物である。</strong>申請が承認待ちのあいだ、予約は
 * 輸送中のまま動かない。却下されても予約は輸送中のままである——却下は
 * 「キャンセルしない」という決定であり、予約を止める決定ではない。
 */
public enum CancellationStatus {

    /** 追跡管理者の判断を待っている。**輸送中の申請だけがここに来る**。 */
    REQUESTED("承認待ち"),

    /** 承認された。予約はキャンセルで確定する。 */
    APPROVED("承認済"),

    /** 却下された。**予約は輸送中のまま維持される**。 */
    REJECTED("却下");

    private final String label;

    CancellationStatus(String label) {
        this.label = label;
    }

    /** 画面に出す名前。**画面が対訳表を持たない**。 */
    public String label() {
        return label;
    }

    /** 判断を待っているか。**未決着の申請は貨物あたり 1 件までである**。 */
    public boolean awaitingDecision() {
        return this == REQUESTED;
    }

    /** 永続化された行から復元する。ここでは選べるかを問わない。 */
    public static CancellationStatus restore(String name) {
        if (name == null) {
            throw new IllegalStateException("キャンセル申請の状態の無い行を読み込みました");
        }
        return valueOf(name);
    }
}
