package com.example.shared.contract;

import java.util.List;

/**
 * 「通関状態が変わった」の契約（US29-5・UC21）。
 *
 * <p><strong>発行側と購読側の両方がここを読む。</strong>片方だけが変えると、送り手は
 * エラーにならないまま届かない。
 *
 * <p><strong>留置は荷主の問題ではなく、社内が動く事象である。</strong>trackingms が
 * 例外「税関保留」を自動で起票し、追跡管理者の未解決一覧に現れる。
 */
public final class CustomsStatusChangedContract {

    private CustomsStatusChangedContract() {
    }

    /**
     * 交換機。
     *
     * <p><strong>荷役の交換機に相乗りする。</strong>送り手は handlingms であり、
     * 荷役のイベントと同じ出口から出る。交換機を増やすと、購読側の宣言と結びつけが
     * そのぶん増える——トピック交換機なので、ルーティングキーを 1 本足すだけで済む。
     */
    public static final String EXCHANGE = "cargoHandlingChannel";

    public static final String ROUTING_KEY = "cargo.customs-status-changed";

    /**
     * 流れる項目。<strong>順序も含めて契約である</strong>。
     *
     * <p><strong>理由は載せる。</strong>キャンセルのイベント（[ADR-025] 決定 3）とは
     * 立場が違う——こちらの行き先は<strong>追跡管理者の画面</strong>であり、認証の内側で
     * ある。何があって留め置かれたかが分からないと、担当者は税関に問い合わせられない。
     */
    public static final List<String> FIELDS = List.of(
            "trackingNumber", "bookingId", "declarationNumber", "fromStatus", "toStatus",
            "reason", "changedBy", "changedAt", "occurredAt");

    /**
     * {@code toStatus} に流れる語彙。
     *
     * <p><strong>項目名だけでは足りない。</strong>状態の値は、送り手では列挙の名前、
     * 受け手では分岐の文字列として<strong>二重に写される</strong>。送り手が状態を足したり
     * 改名したりすると、受け手は「知らない状態」として<strong>何もしない</strong>
     * ——例外にならないのでデッドレターにも行かず、送り手もエラーにならない。
     */
    public static final List<String> STATUSES = List.of("PENDING", "CLEARED", "HELD", "REJECTED");

    /**
     * プロデューサが {@code __TypeId__} に載せる型名。
     *
     * <p>この名前は<strong>コンシューマのクラスパスに存在しない</strong>。
     */
    public static final String PRODUCER_TYPE_ID =
            "com.example.handlingms.application.internal.outboundservices.acl.CustomsStatusChanged";
}
