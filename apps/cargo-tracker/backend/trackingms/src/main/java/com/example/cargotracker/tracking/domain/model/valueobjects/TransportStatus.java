package com.example.cargotracker.tracking.domain.model.valueobjects;

import java.util.EnumSet;
import java.util.Set;

/**
 * 貨物の輸送状態（domain-model.md「TransportStatus 状態遷移」）。<b>trackingms の型</b>。
 *
 * <p><b>契約に載せない。</b> bookingms の {@code BookingStatus} とは別の軸で、
 * 同じ「引取済」でも指すものが違う（あちらは予約の状態）。契約に載せると、片方の
 * BC が値を足しただけでもう一方が復元できなくなる。</p>
 *
 * <p><b>列挙名を利用者に見せない。</b> 画面に {@code NOT_RECEIVED} と出ると、業務
 * 担当者には意味が分からず、マニュアルとも照合できない。呼び名は要素表が正典で、
 * {@code TransportStatusTest#usesTheCanonicalLabel} が突き合わせる（IT8 までこの名前の検査は存在しなかった——書いた保証は赤で固定しないと守られない）。</p>
 *
 * <p><b>遷移は {@link #canTransitionTo} が 1 か所で答える</b>（不変条件 2）。手動更新
 * （US17・IT8）で要るので足した。集約も画面もこの判定を書き直さない——書き直すと、
 * 片方だけ正しく、もう一方が誤りを素通りさせる。</p>
 *
 * <p>荷役からの導出（{@code afterHandling}）は荷役（US15・IT9）で足す。
 * <b>いま要らない判断を先に書かない</b>——書くと、実装が無いまま「守っている」と読める。</p>
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

    /**
     * この状態から {@code next} へ動いてよいか（domain-model.md「TransportStatus 状態遷移」）。
     *
     * <p><b>自分自身へは動かない。</b> 同じ状態への更新は「変わっていない」ので、
     * 履歴に同じ行が積み上がるだけになる。</p>
     *
     * <p><b>{@code DELIVERED} からは動かない。</b> 精算の開始条件なので、後から
     * 戻せると請求が揺れる。</p>
     */
    public boolean canTransitionTo(TransportStatus next) {
        return next != null && allowed().contains(next);
    }

    private Set<TransportStatus> allowed() {
        return switch (this) {
            case NOT_RECEIVED -> EnumSet.of(RECEIVED, MISROUTED);
            case RECEIVED -> EnumSet.of(LOADED, MISROUTED, EXCEPTION);
            case LOADED ->
                    EnumSet.of(IN_TRANSIT, UNLOADED, AWAITING_CLAIM, MISROUTED,
                            EXCEPTION);
            case IN_TRANSIT ->
                    EnumSet.of(UNLOADED, AWAITING_CLAIM, MISROUTED, EXCEPTION);
            case UNLOADED -> EnumSet.of(LOADED, MISROUTED, EXCEPTION);
            case AWAITING_CLAIM -> EnumSet.of(DELIVERED, EXCEPTION);
            case DELIVERED -> EnumSet.noneOf(TransportStatus.class);
            // 再設計のあとに積み直す・降ろし直す。誤配からいきなり引取待ちにはしない。
            case MISROUTED -> EnumSet.of(LOADED, UNLOADED);
            // 解決すると例外前の状態へ戻る。どこへ戻るかは起票前の状態が決める。
            case EXCEPTION ->
                    EnumSet.of(RECEIVED, LOADED, IN_TRANSIT, UNLOADED,
                            AWAITING_CLAIM, DELIVERED);
        };
    }
}
