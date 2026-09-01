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
    SETTLE("精算", Roles.ROLE_ACCOUNTANT, BusinessContextKey.NONE),

    // ---- 例外を起こす工程（US36・[ADR-031] 決定 5）----
    //
    // **専用の入口は作らない。** どれも実利用者が実際に行う操作である。
    // 専用 API を作ると [ADR-026] の検知を通らない経路が生まれ、実際には
    // 動かない実装が緑になる。

    /** 予定より遅い日時で荷役を記録する。遅延はこの記録から起きる。 */
    RECORD_LATE_HANDLING("遅れた荷役記録", Roles.ROLE_HANDLER, BusinessContextKey.NONE,
            Nature.RAISES),
    /** 破損に気づいた人が例外を起票する（US20 のアクターは荷役作業員も含む）。 */
    RAISE_DAMAGE("破損の起票", Roles.ROLE_HANDLER, BusinessContextKey.NONE, Nature.RAISES),
    /** 予定と違う港で荷降しを記録する。誤配はこの記録から検知される（[ADR-026]）。 */
    RECORD_MISROUTED_HANDLING("予定外の港での荷役記録", Roles.ROLE_HANDLER,
            BusinessContextKey.NONE, Nature.RAISES),
    /** 通関を保留にする。税関の判断は担当者の手入力である（UC21）。 */
    HOLD_CUSTOMS("通関の保留", Roles.ROLE_TRACKER, BusinessContextKey.NONE, Nature.RAISES),
    /** 輸送中の予約のキャンセルを申請する（US30）。申請だけでは状態は変わらない。 */
    REQUEST_CANCELLATION("キャンセル申請", Roles.ROLE_SALES, BusinessContextKey.NONE,
            Nature.RAISES),

    // ---- 例外に対応する工程 ----
    //
    // **起こしただけでは仕事にならない。** US36-2 が見たいのは、例外が起きたあとの業務である。

    /** 起きた例外を解決する（[ADR-024]）。 */
    RESOLVE_EXCEPTION("例外の解決", Roles.ROLE_TRACKER, BusinessContextKey.NONE,
            Nature.RESPONDS),
    /**
     * 誤配した港から目的地へ向かう航海を登録する（US36-3）。
     *
     * <p><strong>元の航海では組み直せない。</strong>誤配した港からの区間を持たないため、
     * そのまま割り当てようとすると「選んだ経路はもう使えません」で断られる
     * ——実環境で実際に踏んだ。経路設計者が現在地からの航海を探し、無ければ
     * 登録するのが実業務の手順である。
     */
    REGISTER_RECOVERY_VOYAGE("組み直し用の航海登録", Roles.ROLE_ROUTING,
            BusinessContextKey.RECOVERY_VOYAGE_NUMBER, Nature.RESPONDS),
    /** 現在地から経路を組み直す（US28・US36-3）。元の経路の割り当て直しでは再開しない。 */
    REDESIGN_ROUTE("経路の組み直し", Roles.ROLE_ROUTING, BusinessContextKey.NONE,
            Nature.RESPONDS),
    /** 保留した通関を進める（UC21）。 */
    RELEASE_CUSTOMS("通関の保留解除", Roles.ROLE_TRACKER, BusinessContextKey.NONE,
            Nature.RESPONDS),
    /** キャンセルを承認する。陸揚げ地の指定を伴う（US30）。 */
    APPROVE_CANCELLATION("キャンセル承認", Roles.ROLE_TRACKER, BusinessContextKey.NONE,
            Nature.RESPONDS);

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

    /**
     * 工程の性質。
     *
     * <p>例外を起こす工程と対応する工程を<strong>工程自身が知る</strong>。呼ぶ側で
     * 一覧を持つと、工程を足したときに書き足し忘れた工程だけが性質を持たないまま通る。
     */
    private enum Nature {
        /** 業務の通常の流れ。 */
        PLAIN,
        /** 例外を起こす操作。 */
        RAISES,
        /** 起きた例外に対応する操作。 */
        RESPONDS
    }

    private final String label;
    private final String role;
    private final String producesKey;
    private final Nature nature;

    ScenarioStep(String label, String role, String producesKey) {
        this(label, role, producesKey, Nature.PLAIN);
    }

    ScenarioStep(String label, String role, String producesKey, Nature nature) {
        this.label = label;
        this.role = role;
        this.producesKey = producesKey;
        this.nature = nature;
    }

    /** 例外を起こす工程か。 */
    public boolean raisesException() {
        return nature == Nature.RAISES;
    }

    /** 起きた例外に対応する工程か。 */
    public boolean respondsToException() {
        return nature == Nature.RESPONDS;
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
