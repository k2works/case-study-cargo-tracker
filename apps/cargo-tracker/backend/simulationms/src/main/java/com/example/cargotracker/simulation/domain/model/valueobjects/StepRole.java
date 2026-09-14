package com.example.cargotracker.simulation.domain.model.valueobjects;

/**
 * 工程を実行する担当（[ADR-0020] 決定 2）。
 *
 * <p><b>人と同じロールで叩く。</b> 全部を管理者で叩くと認可を踏まない——
 * 「シミュレーションは通るのに実際の操作は通らない」状態を検出できなくなる。</p>
 *
 * <p>利用者は動作確認用の利用者（ADR-0004）を使う。<b>本番では実行しない</b>ので、
 * この利用者が居る環境でしか動かない。</p>
 */
public enum StepRole {

    /** 営業担当者。荷主・予約・通知・確定。 */
    SALES("sales01"),
    /** 経路設計者。経路の確定・追跡番号の発行。 */
    ROUTING("routing01"),
    /** 追跡管理者。通関の状態更新。 */
    TRACKER("tracker01"),
    /** 荷役作業員。荷役・通関申告・引取。 */
    HANDLER("handler01"),
    /** 経理担当者。請求の発行・入金。 */
    ACCOUNTANT("accountant01");

    private final String username;

    StepRole(String username) {
        this.username = username;
    }

    /** 動作確認用の利用者名（ADR-0004）。 */
    public String username() {
        return username;
    }

    /**
     * その工程を実行する担当。
     *
     * <p><b>業務の分担どおりにする。</b> 実装の都合で担当を変えると、認可の
     * 食い違いがシミュレーションでは出なくなる。</p>
     */
    public static StepRole of(StepKind kind) {
        return switch (kind) {
            case REGISTER_SHIPPER, REGISTER_BOOKING, REQUEST_ROUTING,
                 NOTIFY_SHIPPER, CONFIRM_BOOKING -> SALES;
            case ASSIGN_ROUTE, ISSUE_TRACKING_NUMBER -> ROUTING;
            case RECORD_HANDLING, CLAIM_CARGO -> HANDLER;
            case CLEAR_CUSTOMS -> TRACKER;
            case CALCULATE_INVOICE, ISSUE_INVOICE, RECORD_PAYMENT -> ACCOUNTANT;
        };
    }
}
