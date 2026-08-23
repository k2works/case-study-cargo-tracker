package com.example.trackingms.infrastructure.messaging;

/**
 * 受け取り先の名前（[ADR-022]）。
 *
 * <p>bookingms 側の {@code CargoEventChannels} の<strong>写し</strong>である。サービスが
 * 分かれている以上、定数を共有できない。写しがずれると「送っているのに届かない」形で壊れ、
 * <strong>送り手はエラーにならない</strong>。契約テストが突き合わせる。
 */
public final class TrackingEventChannels {

    public static final String EXCHANGE = "cargoBookingChannel";

    public static final String TRACKING_NUMBER_ISSUED = "cargo.tracking-number-issued";

    /** このサービスが読むキュー。 */
    public static final String QUEUE = "trackingms.tracking-number-issued";

    /**
     * 経路が決まったこと（[ADR-024] 決定 4）。
     *
     * <p>こちらは旅程を持たない。到着の見込みはこれで受け取る。
     */
    public static final String CARGO_ROUTED = "cargo.cargo-routed";

    public static final String CARGO_ROUTED_QUEUE = "trackingms.cargo-routed";

    /** 経路のイベントのデッドレター。 */
    public static final String CARGO_ROUTED_DEAD_LETTER_QUEUE = "trackingms.cargo-routed.dlq";

    /**
     * 受け取れなかったイベントの行き先（[ADR-022] 決定 4）。
     *
     * <p>捨てない。追跡が作られないと、荷主は番号を渡されたのに追えない。しかも送り手は
     * 発行に成功しているので、どこにも異常が残らない。
     */
    public static final String DEAD_LETTER_EXCHANGE = "trackingms.dlx";

    public static final String DEAD_LETTER_QUEUE = "trackingms.tracking-number-issued.dlq";

    /**
     * 荷役の交換機（[ADR-023] 決定 5）。
     *
     * <p>予約の交換機とは分ける。相乗りすると、購読側のキューの結びつけが増えるたびに
     * 無関係なイベントまで配られる。
     */
    public static final String HANDLING_EXCHANGE = "cargoHandlingChannel";

    public static final String HANDLING_ACTIVITY_REGISTERED = "cargo.handling-activity-registered";

    /** 荷役のイベントを読むキュー。 */
    public static final String HANDLING_QUEUE = "trackingms.handling-activity-registered";

    /** 荷役のイベントのデッドレター。 */
    public static final String HANDLING_DEAD_LETTER_QUEUE =
            "trackingms.handling-activity-registered.dlq";

    /**
     * どのキューにも結びつかなかったイベントの行き先（[ADR-022] 決定 4）。
     *
     * <p>デッドレターが守るのは「受け取ったが処理できなかった」だけである。ルーティングキーの
     * 綴りが違う・購読側がまだ配線されていない場合、イベントは<strong>どのキューにも入らず
     * 黙って消える</strong>。しかも発行側は成功を返すため、どこにも異常が残らない。
     *
     * <p>交換機に予備の行き先（alternate-exchange）を持たせ、行き場のないイベントをここへ流す。
     */
    public static final String UNROUTABLE_EXCHANGE = "cargo.unroutable";

    public static final String UNROUTABLE_QUEUE = "cargo.unroutable.queue";

    private TrackingEventChannels() {
    }
}
