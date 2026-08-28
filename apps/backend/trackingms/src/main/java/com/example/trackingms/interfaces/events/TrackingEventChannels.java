package com.example.trackingms.interfaces.events;

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

    /**
     * キャンセルが確定したことのルーティングキー（[ADR-025] 決定 3）。
     *
     * <p>交換機は予約のもの（{@link #EXCHANGE}）に相乗りする。トピック交換機なので、
     * <strong>キューと結びつけを足すだけで済む</strong>。
     */
    public static final String CARGO_CANCELLED = "cargo.cancelled";

    /** キャンセルのイベントを読むキュー。**購読側ごとに分ける**。 */
    public static final String CANCELLED_QUEUE = "trackingms.cargo-cancelled";

    /** キャンセルのイベントのデッドレター。 */
    public static final String CANCELLED_DEAD_LETTER_QUEUE = "trackingms.cargo-cancelled.dlq";

    /**
     * 通関状態が変わったことのルーティングキー（US29-5）。
     *
     * <p>交換機は荷役のもの（{@link #HANDLING_EXCHANGE}）に相乗りする。送り手は同じ
     * handlingms であり、トピック交換機なのでキューと結びつけを足すだけで済む。
     */
    public static final String CUSTOMS_STATUS_CHANGED = "cargo.customs-status-changed";

    /** 通関のイベントを読むキュー。**購読側ごとに分ける**。 */
    public static final String CUSTOMS_QUEUE = "trackingms.customs-status-changed";

    /** 通関のイベントのデッドレター。 */
    public static final String CUSTOMS_DEAD_LETTER_QUEUE =
            "trackingms.customs-status-changed.dlq";

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
