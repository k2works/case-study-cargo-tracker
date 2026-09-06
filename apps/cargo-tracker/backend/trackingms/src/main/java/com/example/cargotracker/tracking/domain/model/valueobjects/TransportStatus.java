package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 貨物の輸送状態（domain-model.md「TransportStatus 状態遷移」）。<b>trackingms の型</b>。
 *
 * <p><b>契約に載せない。</b> bookingms の {@code BookingStatus} とは別の軸で、
 * 同じ「引取済」でも指すものが違う（あちらは予約の状態）。契約に載せると、片方の
 * BC が値を足しただけでもう一方が復元できなくなる。</p>
 *
 * <p><b>列挙名を利用者に見せない。</b> 画面に {@code NOT_RECEIVED} と出ると、業務
 * 担当者には意味が分からず、マニュアルとも照合できない。呼び名は要素表が正典で、
 * {@code TransportStatusLabelTest} が突き合わせる。</p>
 *
 * <p>遷移（{@code canTransitionTo}）と荷役からの導出（{@code afterHandling}）は
 * 荷役（US15・IT9）で足す。<b>いま要らない判断を先に書かない</b>——書くと、実装が
 * 無いまま「守っている」と読める。</p>
 */
public enum TransportStatus {
    /** 追跡を開始した直後。まだ荷物を受け取っていない。 */
    NOT_RECEIVED("未受領"),
    /** 出発地で受け取った。 */
    RECEIVED("受領済"),
    /** 船に積み込んだ。 */
    LOADED("積込済"),
    /** 輸送中。荷役では起きない（手動更新・US17）。 */
    IN_TRANSIT("輸送中"),
    /** 途中の港で荷降しした。 */
    UNLOADED("荷降し済"),
    /** 目的港で荷降しされ、荷受人の引取を待っている。 */
    AWAITING_CLAIM("引取待ち"),
    /** 引き取られた。精算の開始条件。 */
    DELIVERED("引取済"),
    /** 予定ルート外の荷役を受けた。 */
    MISROUTED("誤配"),
    /** 未解決の例外がある。 */
    EXCEPTION("例外発生");

    private final String label;

    TransportStatus(String label) {
        this.label = label;
    }

    /** 利用者に見せる呼び名。 */
    public String label() {
        return label;
    }
}
