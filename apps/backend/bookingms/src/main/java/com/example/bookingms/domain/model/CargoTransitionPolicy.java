package com.example.bookingms.domain.model;

import java.util.Optional;

/**
 * その状態で何ができないか（[ADR-020]・[ADR-021]）。
 *
 * <p><strong>述語と操作が同じ条件を二度書かない。</strong>二度書くと、片方だけ直したときに
 * 画面のボタンと API の応答が食い違う（IT6 で最も多かった欠陥の形）。集約はここに問い、
 * 述語も操作もこの答えを使う。
 *
 * <p>理由ごとに文言を分けるのは、断りが「何を直せばよいか」を伝えるものだからである。
 * 1 つにまとめると、利用者は次に何をすればよいか分からない。
 *
 * <p>集約から切り出したのは、割る基準（1 ファイル 500 行）を超えたからだけではない。
 * <strong>「いまの状態で何ができるか」は、貨物そのものとは変わる理由が違う</strong>
 * ——状態の遷移規則が増えるのは業務の手番が増えるときで、貨物の属性が増えるときではない。
 *
 * @param status いまの状態
 * @param trackingNumberIssued 追跡番号を発行済みか
 */
record CargoTransitionPolicy(CargoStatus status, boolean trackingNumberIssued) {

    /**
     * 経路設計を依頼できない理由。できるなら空を返す。
     *
     * <p><strong>述語と操作が同じ条件を二度書かない。</strong>二度書くと、片方だけ直したとき
     * 画面のボタンと API の応答が食い違う（IT6 で最も多かった欠陥の形）。
     *
     * <p>IT5 で {@code ROUTE_PROPOSED} が増え、最初の検査は実際に働くようになった
     * （[ADR-020] の影響）。経路が決まった予約への再依頼は、経路の状態より先にここで落ちる。
     */
    Optional<String> reasonCannotRequestRouting() {
        if (status.booking() != BookingStatus.PRELIMINARY) {
            return Optional.of("仮受付の予約だけが経路設計を依頼できます");
        }
        if (status.routing() == RoutingStatus.ROUTING_REQUESTED) {
            return Optional.of("この予約はすでに経路設計を依頼しています");
        }
        if (status.routing() == RoutingStatus.ROUTED) {
            return Optional.of("この予約はすでに経路が決まっています");
        }
        return Optional.empty();
    }

    /** 経路を割り当てられない理由。できるなら空を返す（旅程そのものの妥当性は含まない）。 */
    Optional<String> reasonCannotAssignItinerary() {
        if (status.routing() != RoutingStatus.ROUTING_REQUESTED
                && status.routing() != RoutingStatus.ROUTED) {
            return Optional.of("経路設計を依頼された予約にだけ経路を割り当てられます");
        }
        if (status.booking() == BookingStatus.CONFIRMED
                || status.booking() == BookingStatus.TRACKING_ISSUED) {
            return Optional.of(
                    "確定した予約の経路は差し替えられません。変更が必要なら担当者に相談してください");
        }
        return Optional.empty();
    }

    /** 追跡番号を発行できない理由。できるなら空を返す。 */
    Optional<String> reasonCannotIssueTrackingNumber() {
        if (status.booking() != BookingStatus.CONFIRMED) {
            return Optional.of("確定した予約にだけ追跡番号を発行できます");
        }
        if (trackingNumberIssued) {
            return Optional.of("この予約はすでに追跡番号を発行しています");
        }
        return Optional.empty();
    }
}
