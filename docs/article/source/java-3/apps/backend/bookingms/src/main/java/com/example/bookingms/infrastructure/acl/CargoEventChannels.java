package com.example.bookingms.infrastructure.acl;

/**
 * イベントの流れ先の名前（[ADR-022]）。
 *
 * <p>文字列を配線のあちこちに書くと、片方だけ直したときに「送っているのに届かない」形で壊れる。
 * 送り手と受け手は別のサービスなので、<strong>名前は写しになる</strong>。写しであることを
 * 契約テストが突き合わせる。
 */
public final class CargoEventChannels {

    /**
     * 予約に起きたことを流す交換機。
     *
     * <p>名前は設計の一覧（`architecture_backend.md`）の `cargoBookingChannel` を引き継ぐ。
     * 運ぶ中身は変わったが（[ADR-022] 決定 1）、既存の設定を変えない。
     */
    public static final String EXCHANGE = "cargoBookingChannel";

    /** 追跡番号を発行したことのルーティングキー。 */
    public static final String TRACKING_NUMBER_ISSUED = "cargo.tracking-number-issued";

    /**
     * キャンセルが確定したことのルーティングキー（[ADR-025] 決定 3）。
     *
     * <p><strong>交換機は増やさない。</strong>トピック交換機なので、ルーティングキーを
     * 1 本足すだけで済む。
     */
    public static final String CARGO_CANCELLED = "cargo.cancelled";

    /**
     * 荷役の交換機（[ADR-023] 決定 5・[ADR-025] 決定 1）。
     *
     * <p><strong>handlingms・trackingms と同じ名前・同じ引数で宣言する。</strong>
     * 引数が食い違うと {@code PRECONDITION_FAILED} で落ち、<strong>後続のキュー宣言まで
     * 止まる</strong>。既存の環境では宣言し直せないため、これは Testcontainers では出ず
     * kind で初めて出る形である。
     */
    public static final String HANDLING_EXCHANGE = "cargoHandlingChannel";

    public static final String HANDLING_ACTIVITY_REGISTERED = "cargo.handling-activity-registered";

    /**
     * 荷役のイベントを読むキュー。
     *
     * <p><strong>購読側ごとにキューを分ける</strong>（トピック交換機 + 購読者ごとのキュー）。
     * 共有すると、片方が読んだイベントをもう片方が受け取れない。
     */
    public static final String HANDLING_QUEUE = "bookingms.handling-activity-registered";

    /** 荷役のイベントのデッドレター。 */
    public static final String HANDLING_DEAD_LETTER_QUEUE =
            "bookingms.handling-activity-registered.dlq";

    /** 受け取れなかったイベントの行き先。 */
    public static final String DEAD_LETTER_EXCHANGE = "bookingms.dlx";

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

    private CargoEventChannels() {
    }
}
