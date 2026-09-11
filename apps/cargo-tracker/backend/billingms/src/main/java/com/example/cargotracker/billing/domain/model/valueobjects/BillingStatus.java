package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 請求書の状態（domain-model.md「Billing Context」）。
 *
 * <p><b>{@code PENDING} はこの版の経路では通らない。</b> 算出の起点は
 * {@code CargoDeliveredEvent} で、<b>算出できたときに初めて集約ができる</b>。
 * 算出できない（重量が分からない・荷主のスナップショットが無い）ときは請求書を
 * 作らず、要確認一覧へ出す。列挙に残しているのは、US23 以降で「算出待ち」を
 * 明示的に作る余地を消さないためである。<b>書いてあるのに通らない値は、次に読む
 * 人が使おうとする</b>ので、ここに注記しておく。</p>
 */
public enum BillingStatus {

    /** 算出待ち。<b>本プロジェクトの経路では通らない</b>（上の注記）。 */
    PENDING("算出待ち"),

    /** 算出済（US21）。 */
    CALCULATED("算出済"),

    /** 請求済（US23・IT14）。 */
    INVOICED("請求済"),

    /** 入金済（US23・IT14）。 */
    PAID("入金済"),

    /** 取消（US23・IT14）。 */
    VOID("取消");

    private final String label;

    BillingStatus(String label) {
        this.label = label;
    }

    /** 画面に出す呼び名。<b>列挙名を出さない</b>（読む人は業務の言葉で読む）。 */
    public String label() {
        return label;
    }

    /** 調整を受け付ける状態か（US21 §受入基準 6）。 */
    public boolean acceptsAdjustment() {
        return this == CALCULATED;
    }
}
