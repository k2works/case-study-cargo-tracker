package com.example.bookingms.domain.model.aggregates;

import java.util.Optional;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoStatus;
import com.example.bookingms.domain.model.valueobjects.RoutingStatus;

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

    /**
     * その荷役のあと、予約はどの状態になるか（[ADR-025] 決定 1）。
     *
     * <p><strong>巻き戻さない。</strong>再試行やデッドレターからの送り直しで、荷役の
     * 届く順は入れ替わる。順序を信じて上書きすると、あとから届いた古い作業で予約が
     * 輸送中へ戻り、荷主は「配送完了だったはずの貨物が輸送中に戻っている」を見る。
     *
     * <p><strong>キャンセル済みの予約は動かない。</strong>遅れて届いた荷役でキャンセルが
     * 覆ると、荷主との約束と記録が食い違う。
     *
     * <p><strong>冪等である。</strong>同じ荷役が 2 回届いても、2 回目は空を返す。
     *
     * @return 進む先。動かないなら空
     */
    Optional<BookingStatus> bookingStatusAfterHandling(String handlingType) {
        if (status.booking() == BookingStatus.CANCELLED) {
            return Optional.empty();
        }
        return BookingStatus.afterHandling(handlingType).filter(status.booking()::canAdvanceTo);
    }

    /**
     * キャンセルを申請できない理由（US30-1）。
     *
     * <p>判定をここに置くのは、<strong>可否の判定を 1 か所に集めるため</strong>である。
     * 集約に散らすと、状態を足したときに直す場所が増える。
     *
     * <p>配送完了はすでに荷受人へ引き渡しており、キャンセルする対象が無い。
     */
    Optional<String> reasonCannotCancel() {
        return switch (status.booking()) {
            case CANCELLED -> Optional.of("この予約はすでにキャンセルされています");
            case DELIVERED -> Optional.of("配送が完了した予約はキャンセルできません");
            default -> Optional.empty();
        };
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
