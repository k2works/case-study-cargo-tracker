package com.example.bookingms.interfaces.rest;

/**
 * 予約に対していま行える操作（[ADR-021]）。
 *
 * <p><strong>可否を決めるのは集約である。</strong>画面やモックが状態名を見比べて同じ判断を
 * 組み立てると、遷移の規則が集約・画面・モックの 3 か所に分かれ、片方だけ直る形になる
 * ——IT6 のふりかえりで最も多かった欠陥の形である。
 *
 * <p>応答に載せるのは<strong>結果</strong>（何ができるか）だけで、理由や状態の組み合わせは
 * 載せない。画面が理由から判断を組み直せると、また同じことが起きる。
 */
public enum BookingAction {
    /** 経路設計を依頼する（営業）。 */
    REQUEST_ROUTING,
    /** 経路を割り当てる（経路設計者）。 */
    ASSIGN_ROUTE,
    /** 条件の協議を営業へ戻す（経路設計者）。 */
    REQUEST_CONSULTATION,
    /** 荷主へ通知する（営業）。 */
    NOTIFY_SHIPPER,
    /** 確定する（営業）。 */
    CONFIRM,
    /** 経路設計へ戻す（営業）。 */
    RETURN_TO_ROUTING,
    /** 追跡番号を発行する（経路設計者）。 */
    ISSUE_TRACKING_NUMBER,
    /** 日程を直す（営業）。 */
    REVISE_SCHEDULE
}
