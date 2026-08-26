package com.example.bookingms.domain.model;

/** 予約の状態。荷主との約束がどこまで進んだかを表す（[ADR-020]）。 */
public enum BookingStatus {
    /** 仮受付。経路設計の対象になる。 */
    PRELIMINARY,

    /**
     * 経路を提示できる状態（US09 / US11）。
     *
     * <p>確定（`CONFIRMED`）ではない。<strong>確定は荷主の合意を経た別の作業</strong>（US13）であり、
     * 経路が決まっただけで確定にすると、荷主が見ていない条件で契約が成立したことになる。
     */
    ROUTE_PROPOSED,

    /**
     * 荷主へ経路を提示した（US12・[ADR-021] 決定 1）。<strong>荷主の手番</strong>。
     *
     * <p>状態にするのは、通知していない予約を確定できないようにするためである。確定は
     * 「荷主の合意を得た」という業務上の事実であり、提示していない条件で合意は成り立たない。
     * 記録の有無で代用すると、守るのは呼び出し側の作法になる。
     *
     * <p>もう一度通知してもここに留まる（決定 2）。
     */
    ROUTE_NOTIFIED,

    /**
     * 荷主の合意を得て確定した（US13）。<strong>経路設計者の手番</strong>（追跡番号を発行する）。
     *
     * <p><strong>ここから経路設計へは戻せない</strong>（決定 3）。確定は追跡番号の発行と荷役の
     * 起点であり、戻せるようにすると荷役の担当者と荷主が別の予定を見る。
     */
    CONFIRMED,

    /**
     * 追跡番号を発行した（US14）。<strong>荷役の手番</strong>（貨物を受け取る）。
     */
    TRACKING_ISSUED,

    /**
     * 輸送中（US30・[ADR-025] 決定 1）。<strong>荷役のイベントで知る</strong>。
     *
     * <p><strong>キャンセルに承認が要る境目である。</strong>ここから先は貨物が船の上に
     * あり、どこで降ろすかを決めないとキャンセルできない。状態を持たないと
     * 「承認が要るかどうか」を判断できず、輸送中の貨物が即時にキャンセルされる。
     *
     * <p>bookingms は自分では知らない——荷役の記録が一次情報である。
     * {@code HandlingActivityRegisteredEvent} を購読して進める。
     */
    IN_TRANSIT,

    /**
     * 配送完了（US30）。引取（CLAIM）の荷役で到達する。
     *
     * <p>ここから先はキャンセルできない。すでに荷受人へ引き渡している。
     */
    DELIVERED,

    /**
     * キャンセル確定（US30）。
     *
     * <p>輸送開始前は即時に、輸送中は<strong>追跡管理者の承認を経て</strong>ここへ来る。
     * 承認を迂回する経路を作らない（{@code BookingStatusTest#cancelsOnlyThroughTheAggregate}）。
     */
    CANCELLED,

    /**
     * 精算済（US23・[ADR-028] 決定 1）。<strong>引取済からだけ来る。</strong>
     *
     * <p>入金を確認した billingms が知らせる。**bookingms は自分では知らない**
     * ——請求と入金は経理の仕事であり、予約の側に判断材料が無い。
     *
     * <p>並びの最後に置いているが、{@link #canAdvanceTo} は順序では判定しない
     * ——キャンセルされた予約に「精算済」は無い。
     */
    SETTLED;

    /**
     * その荷役で予約はどこまで進むか（[ADR-025] 決定 1）。
     *
     * <p><strong>対応はここだけが持つ。</strong>集約や購読側で書き直すと、荷役の意味が
     * 2 か所に分かれる（{@code TrackingStatus#afterHandling} と同じ形）。
     *
     * <p><strong>受領では輸送中にしない。</strong>まだ港にあり、船に載っていない。
     * 積み替えの荷降し（UNLOAD）でも輸送中のままである——途中の港で降ろしても、
     * 輸送は終わっていない。
     *
     * @param handlingType 荷役の種別（RECEIVE / LOAD / UNLOAD / CLAIM）
     * @return 進む先。その荷役では動かないなら空
     */
    public static java.util.Optional<BookingStatus> afterHandling(String handlingType) {
        return java.util.Optional.ofNullable(switch (handlingType) {
            case "LOAD" -> IN_TRANSIT;
            case "CLAIM" -> DELIVERED;
            default -> null;
        });
    }

    /**
     * この状態から、その状態へ進めるか。
     *
     * <p><strong>並び順で判定する。</strong>再試行やデッドレターからの送り直しで荷役の
     * 届く順は入れ替わる。順序を信じて上書きすると、あとから届いた古い作業で予約が
     * 巻き戻り、荷主は「配送完了だったはずの貨物が輸送中に戻っている」を見る。
     */
    public boolean canAdvanceTo(BookingStatus next) {
        // **精算済は順序では決めない。** 並びの最後に置いてあるため順序で判定すると、
        // キャンセルされた予約まで精算済にできてしまう——運んでいない予約に
        // 「精算済」は無い
        if (next == SETTLED) {
            return this == DELIVERED;
        }
        if (this == SETTLED) {
            // 精算が済んだ予約は動かない。遅れて届いた荷役で巻き戻らない
            return false;
        }
        return next.ordinal() > ordinal();
    }
}
