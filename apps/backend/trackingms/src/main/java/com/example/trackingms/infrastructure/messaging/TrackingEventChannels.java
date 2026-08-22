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
     * 受け取れなかったイベントの行き先（[ADR-022] 決定 4）。
     *
     * <p>捨てない。追跡が作られないと、荷主は番号を渡されたのに追えない。しかも送り手は
     * 発行に成功しているので、どこにも異常が残らない。
     */
    public static final String DEAD_LETTER_EXCHANGE = "trackingms.dlx";

    public static final String DEAD_LETTER_QUEUE = "trackingms.tracking-number-issued.dlq";

    private TrackingEventChannels() {
    }
}
