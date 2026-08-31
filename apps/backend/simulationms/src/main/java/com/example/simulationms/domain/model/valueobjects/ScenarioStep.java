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
    REGISTER_SHIPPER("荷主登録", Roles.ROLE_SALES, BusinessContextKey.SHIPPER_ID),
    REGISTER_BOOKING("予約登録", Roles.ROLE_SALES, BusinessContextKey.BOOKING_ID),
    REQUEST_ROUTING("経路設計依頼", Roles.ROLE_SALES, BusinessContextKey.NONE),
    // **航海が 1 本も無い環境では候補が出ない。**「候補が無いので飛ばす」にすると、
    // 通っていないことに気づけないまま「全工程成功」で終わる（IT5 の Try 2）。
    // 経路設計者が実際に踏む操作でもあるため、工程として置く
    REGISTER_VOYAGE("航海登録", Roles.ROLE_ROUTING, BusinessContextKey.VOYAGE_NUMBER),
    ASSIGN_ROUTE("経路割り当て", Roles.ROLE_ROUTING, BusinessContextKey.NONE),
    // 通知してからでないと確定できない（US12-4）。工程から落とすと、確定が必ず断られる
    NOTIFY_ROUTE("経路通知", Roles.ROLE_SALES, BusinessContextKey.NONE),
    CONFIRM_BOOKING("予約確定", Roles.ROLE_SALES, BusinessContextKey.NONE),
    ISSUE_TRACKING_NUMBER("追跡番号発行", Roles.ROLE_ROUTING, BusinessContextKey.TRACKING_NUMBER),
    RECORD_HANDLING("荷役記録", Roles.ROLE_HANDLER, BusinessContextKey.NONE),
    DECLARE_CUSTOMS("通関申告", Roles.ROLE_HANDLER, BusinessContextKey.DECLARATION_ID),
    CLEAR_CUSTOMS("通関完了", Roles.ROLE_TRACKER, BusinessContextKey.NONE),
    RECORD_CLAIM("引取記録", Roles.ROLE_HANDLER, BusinessContextKey.NONE),
    CALCULATE_CHARGE("料金算出", Roles.ROLE_ACCOUNTANT, BusinessContextKey.INVOICE_NUMBER),
    SETTLE("精算", Roles.ROLE_ACCOUNTANT, BusinessContextKey.NONE);

    /** ロールの名前。**書き並べない**——写し間違えた 1 つだけが誰でもない者になる。 */
    private static final class Roles {
        private static final String ROLE_SALES = "ROLE_SALES";
        private static final String ROLE_ROUTING = "ROLE_ROUTING";
        private static final String ROLE_HANDLER = "ROLE_HANDLER";
        private static final String ROLE_TRACKER = "ROLE_TRACKER";
        private static final String ROLE_ACCOUNTANT = "ROLE_ACCOUNTANT";

        private Roles() {
        }
    }

    private final String label;
    private final String role;
    private final String producesKey;

    ScenarioStep(String label, String role, String producesKey) {
        this.label = label;
        this.role = role;
        this.producesKey = producesKey;
    }

    public String label() {
        return label;
    }

    /** その工程を踏む利用者のロール。 */
    public String role() {
        return role;
    }

    /**
     * その工程が生む識別子の名前。生まない工程は空。
     *
     * <p>引き継ぎ先を工程自身が持つ。呼び出し側で対応表を書くと、工程を足したときに
     * 書き足し忘れた工程だけが<strong>何も引き継がない</strong>まま通る。
     */
    public String producesKey() {
        return producesKey;
    }

    /** 識別子を生む工程か。 */
    public boolean produces() {
        return !producesKey.isEmpty();
    }
}
