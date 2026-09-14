package com.example.cargotracker.simulation.domain.model.valueobjects;

/**
 * 業務シミュレーションの工程（US33 §受入基準 1 / US34 §受入基準 1）。
 *
 * <p><b>業務の言葉で並べる。</b> 画面は「どの工程で止まったか」を読む人のために
 * あるので、`POST /api/v1/booking/bookings` ではなく「予約の登録」と出す。</p>
 *
 * <p><b>呼ぶ API は工程が知らない。</b> ここにあるのは業務の段取りだけで、
 * どの経路を叩くかは実行側が決める——段取りと経路を同じ場所に書くと、
 * API を変えるたびにシナリオの定義が動く。</p>
 */
public enum StepKind {

    /** 荷主を登録する。**シミュレーション由来の印はここで付く**（注 N3）。 */
    REGISTER_SHIPPER("荷主の登録"),
    /** 貨物予約を登録する。 */
    REGISTER_BOOKING("予約の登録"),
    /** 経路設計へ引き渡す。 */
    REQUEST_ROUTING("経路設計への引き渡し"),
    /** 経路候補から 1 つ選んで確定する。**候補 0 件はここで止まる**。 */
    ASSIGN_ROUTE("経路の確定"),
    /** 荷主へ通知した記録を残す（送信基盤はスコープ外）。 */
    NOTIFY_SHIPPER("荷主への通知"),
    /** 予約を確定する。 */
    CONFIRM_BOOKING("予約の確定"),
    /** 追跡番号を発行する。**ここから追跡と荷役が始まる**。 */
    ISSUE_TRACKING_NUMBER("追跡番号の発行"),
    /** 受領・積込・荷降しを順に記録する。 */
    RECORD_HANDLING("荷役の記録"),
    /** 通関申告を出して通関済にする。**引取の前提**（US29）。 */
    CLEAR_CUSTOMS("通関"),
    /** 荷受人の確認を取って引取を記録する。 */
    CLAIM_CARGO("引取"),
    /** 輸送料金が算出されるのを待つ（連鎖が作る）。 */
    CALCULATE_INVOICE("料金の算出"),
    /** 請求書を発行する。 */
    ISSUE_INVOICE("請求書の発行"),
    /** 入金を記録する。**ここで予約が精算済になる**。 */
    RECORD_PAYMENT("入金の記録");

    private final String label;

    StepKind(String label) {
        this.label = label;
    }

    /** 画面に出す呼び名。<b>列挙名を出さない</b>（読む人は業務の言葉で読む）。 */
    public String label() {
        return label;
    }
}
