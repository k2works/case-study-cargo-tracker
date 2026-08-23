package com.example.shared.contract;

import java.util.List;

/**
 * 追跡番号を発行したことのイベント契約（[ADR-022]）。
 *
 * <p><strong>両側が同じ 1 つを読む。</strong>これまでは項目の名簿と交換機・ルーティングキーが
 * プロデューサ側（bookingms）とコンシューマ側（trackingms）の両方に写しとして置かれていた。
 * 写しがずれると「送っているのに届かない」形で壊れ、しかも<strong>送り手はエラーにならない</strong>。
 * 両側の写しを同時に直すことに頼るのは、直し忘れを検出できない。
 *
 * <p>ここに置くのは<strong>契約であって実装ではない</strong>。イベントの DTO は BC ごとに
 * 持つ（相手の型を持ち込まない）。共有するのは「両者が合意した名前と項目」だけである。
 *
 * <p>testFixtures に置くのは、これがテストの道具だからである。本番のコードはこれを読まない
 * ——読ませると、共有カーネルに業務の契約が入り込み、片方の変更が両サービスの再デプロイに
 * なる（{@code sharedKernelScopeRule} が守っている境目）。
 */
public final class TrackingNumberIssuedContract {

    private TrackingNumberIssuedContract() {
    }

    /** 交換機。 */
    public static final String EXCHANGE = "cargoBookingChannel";

    /** ルーティングキー。 */
    public static final String ROUTING_KEY = "cargo.tracking-number-issued";

    /**
     * 流れる項目。<strong>順序も含めて契約である</strong>。
     *
     * <p>両側の DTO の要素名がこれと一致することを、それぞれの契約テストが確かめる。
     * 片側が項目を足すと、足したほうのテストが赤になる。
     */
    public static final List<String> FIELDS = List.of(
            "trackingNumber", "bookingId", "originUnLocode", "destinationUnLocode",
            "arrivalDeadline", "occurredAt");

    /**
     * プロデューサが {@code __TypeId__} に載せる型名。
     *
     * <p>この名前は<strong>コンシューマのクラスパスに存在しない</strong>。それでも読めることが
     * 「相手の型を共有しない」という判断の根拠であり、コンシューマ側の契約テストが確かめる。
     */
    public static final String PRODUCER_TYPE_ID =
            "com.example.bookingms.application.port.TrackingNumberIssued";
}
