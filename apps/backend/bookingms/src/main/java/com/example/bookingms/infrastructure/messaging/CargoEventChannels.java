package com.example.bookingms.infrastructure.messaging;

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

    private CargoEventChannels() {
    }
}
