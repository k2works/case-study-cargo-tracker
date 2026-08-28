package com.example.shared.contract;

import java.util.List;

/**
 * 「キャンセルが確定した」の契約（US30・[ADR-025] 決定 3）。
 *
 * <p><strong>発行側と購読側の両方がここを読む。</strong>片方だけが変えると、送り手は
 * エラーにならないまま届かない。
 *
 * <p><strong>購読者がいるから発行する。</strong>公開追跡が開いているため、キャンセルが
 * 承認された貨物を荷主が引くと trackingms は「輸送中」のまま返す——荷主は自分が
 * 申し入れて承認されたキャンセルを、画面で否定されることになる。
 */
public final class CargoCancelledContract {

    private CargoCancelledContract() {
    }

    /**
     * 交換機。
     *
     * <p><strong>既存の予約の交換機に相乗りする。</strong>交換機を増やすと、購読側の
     * 宣言と結びつけがそのぶん増える。トピック交換機なので、ルーティングキーを 1 本
     * 足すだけで済む。
     */
    public static final String EXCHANGE = "cargoBookingChannel";

    public static final String ROUTING_KEY = "cargo.cancelled";

    /**
     * 流れる項目。<strong>順序も含めて契約である</strong>。
     *
     * <p><strong>{@code reason} は載せない。</strong>このイベントが行き着く先は
     * <strong>公開の追跡照会</strong>——認証の無い画面である。社内の判断（誰の都合で
     * 止めたか、どんな事情か）を、追跡番号を手に入れた誰もが読める場所へ流さない。
     *
     * <p>陸揚げ地も載せない。荷主に伝えるのは「キャンセルが確定した」ことであり、
     * どこで降ろすかは社内の手配である。
     */
    public static final List<String> FIELDS = List.of(
            "trackingNumber", "bookingId", "cancelledAt", "occurredAt");

    /**
     * <strong>載せてはいけない項目。</strong>
     *
     * <p>「載せる項目」の一覧だけだと、足したときに気づけない——一覧に無い項目が
     * 流れていても、検査は「載せるべきものが揃っている」としか言わない。
     */
    public static final List<String> FORBIDDEN_FIELDS = List.of(
            "reason", "dischargeLocationUnLocode", "requestedBy", "decidedBy", "decisionReason");

    /**
     * プロデューサが {@code __TypeId__} に載せる型名。
     *
     * <p>この名前は<strong>コンシューマのクラスパスに存在しない</strong>。
     */
    public static final String PRODUCER_TYPE_ID =
            "com.example.bookingms.application.internal.outboundservices.acl.CargoCancelled";
}
