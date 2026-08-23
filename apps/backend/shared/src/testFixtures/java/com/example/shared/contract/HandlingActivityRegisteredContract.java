package com.example.shared.contract;

import java.util.List;

/**
 * 荷役作業を記録したことのイベント契約（[ADR-023] 決定 5）。
 *
 * <p><strong>両側が同じ 1 つを読む。</strong>写しを 2 つ置くと、片方だけ直したことを誰も
 * 検出できない（IT7 返済枠 0.12 と同じ形）。
 *
 * <p>共有するのは「両者が合意した名前と項目」だけである。イベントの DTO は BC ごとに持つ。
 */
public final class HandlingActivityRegisteredContract {

    private HandlingActivityRegisteredContract() {
    }

    /**
     * 交換機。
     *
     * <p>予約の交換機（{@code cargoBookingChannel}）とは分ける。相乗りすると、購読側の
     * キューの結びつけが増えるたびに無関係なイベントまで配られる。
     */
    public static final String EXCHANGE = "cargoHandlingChannel";

    /** ルーティングキー。 */
    public static final String ROUTING_KEY = "cargo.handling-activity-registered";

    /** 流れる項目。<strong>順序も含めて契約である</strong>。 */
    public static final List<String> FIELDS = List.of(
            "trackingNumber", "bookingId", "type", "locationUnLocode", "completionTime",
            "voyageNumber", "offRoute", "occurredAt");

    /**
     * プロデューサが {@code __TypeId__} に載せる型名。
     *
     * <p>この名前は<strong>コンシューマのクラスパスに存在しない</strong>。それでも読めることが
     * 「相手の型を共有しない」という判断の根拠であり、コンシューマ側の契約テストが確かめる。
     */
    public static final String PRODUCER_TYPE_ID =
            "com.example.handlingms.application.port.HandlingActivityRegistered";
}
