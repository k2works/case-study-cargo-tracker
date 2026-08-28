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

    /**
     * {@code type} に流れる語彙。
     *
     * <p><strong>項目名だけでは足りない。</strong>種別の値は、送り手では列挙の名前、
     * 受け手では遷移を決める分岐の文字列として<strong>二重に写される</strong>。
     * 送り手が種別を足したり改名したりすると、受け手は「知らない種別」として
     * <strong>何もしない</strong>——例外にならないのでデッドレターにも予備の交換機にも
     * 行かず、送り手もエラーにならない。IT7 の契約テスト一式が守ろうとした
     * 「送っているのに届かない、しかも誰も気づかない」が、この 1 項目だけ素通りになる。
     *
     * <p>US17（出港）・US28（誤配）で種別が増える IT8・IT10 に直接効く。
     */
    public static final List<String> TYPES = List.of("RECEIVE", "LOAD", "UNLOAD", "CLAIM");

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
            "com.example.handlingms.application.internal.outboundservices.acl.HandlingActivityRegistered";
}
