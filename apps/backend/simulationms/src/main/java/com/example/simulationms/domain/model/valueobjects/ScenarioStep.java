package com.example.simulationms.domain.model.valueobjects;

/**
 * シミュレーションが踏む業務の工程（[ADR-030] 決定 2）。
 *
 * <p>並びは業務の順序そのものである。<strong>すべて実利用者が踏む経路</strong>であり、
 * 内部 API は 1 つも含まない。含めると、認可を素通りする経路を新設することになる。
 *
 * <p>{@code role} が工程ごとに違うのは意図である。1 つの利用者に全ロールを与えると、
 * <strong>本番には存在しない権限の持ち主</strong>を作ることになる。
 */
public enum ScenarioStep {
    REGISTER_SHIPPER("荷主登録", "ROLE_SALES"),
    REGISTER_BOOKING("予約登録", "ROLE_SALES"),
    REQUEST_ROUTING("経路設計依頼", "ROLE_SALES"),
    ASSIGN_ROUTE("経路割り当て", "ROLE_ROUTING"),
    CONFIRM_BOOKING("予約確定", "ROLE_SALES"),
    ISSUE_TRACKING_NUMBER("追跡番号発行", "ROLE_ROUTING"),
    RECORD_HANDLING("荷役記録", "ROLE_HANDLER"),
    DECLARE_CUSTOMS("通関申告", "ROLE_HANDLER"),
    CLEAR_CUSTOMS("通関完了", "ROLE_TRACKER"),
    RECORD_CLAIM("引取記録", "ROLE_HANDLER"),
    CALCULATE_CHARGE("料金算出", "ROLE_ACCOUNTANT"),
    SETTLE("精算", "ROLE_ACCOUNTANT");

    private final String label;
    private final String role;

    ScenarioStep(String label, String role) {
        this.label = label;
        this.role = role;
    }

    public String label() {
        return label;
    }

    /** その工程を踏む利用者のロール。 */
    public String role() {
        return role;
    }
}
