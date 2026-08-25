package com.example.handlingms.infrastructure.messaging;

/**
 * 送り先の名前（[ADR-023] 決定 5・[ADR-022] の形を写す）。
 *
 * <p>購読側（trackingms）にも同じ名前の写しがある。サービスが分かれている以上、定数を
 * 共有できない。写しがずれると「送っているのに届かない」形で壊れ、<strong>送り手は
 * エラーにならない</strong>。契約テストが共有の契約と突き合わせる。
 */
public final class HandlingEventChannels {

    /**
     * 荷役の交換機。
     *
     * <p>予約の交換機（{@code cargoBookingChannel}）とは分ける。同じ交換機に相乗りすると、
     * 購読側のキューの結びつけが増えるたびに、無関係なイベントまで配られる。
     */
    public static final String EXCHANGE = "cargoHandlingChannel";

    public static final String HANDLING_ACTIVITY_REGISTERED = "cargo.handling-activity-registered";

    /**
     * 通関状態が変わったことのルーティングキー（US29-5）。
     *
     * <p><strong>交換機は増やさない。</strong>送り手は同じ handlingms であり、
     * トピック交換機なのでルーティングキーを 1 本足すだけで済む。
     */
    public static final String CUSTOMS_STATUS_CHANGED = "cargo.customs-status-changed";

    /**
     * どのキューにも結びつかなかったイベントの行き先（[ADR-022] 決定 4）。
     *
     * <p>デッドレターが守るのは「受け取ったが処理できなかった」だけである。
     * ルーティングキーの綴り違いや購読側の配線漏れでは、イベントはどのキューにも入らず
     * 黙って消える。
     */
    public static final String UNROUTABLE_EXCHANGE = "cargo.unroutable";

    public static final String UNROUTABLE_QUEUE = "cargo.unroutable.queue";

    private HandlingEventChannels() {
    }
}
