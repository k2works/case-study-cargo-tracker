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
 * <p><b>荷役からの導出は {@link #afterHandling} が 1 か所で答える</b>（US15・IT9）。
 * 「同じ荷降しでも行き先が違う」判定を集約や購読側に書き直さない
 * （domain-model.md「TransportStatus 状態遷移」）。</p>
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
     * 荷役のあとの状態（US15 / domain-model.md）。
     *
     * <p><b>同じ荷降しでも行き先が違う。</b> 途中の港なら {@code UNLOADED}、
     * 目的港なら {@code AWAITING_CLAIM} で、引取を待つ状態になる。この判定を
     * 集約にも購読側にも書き直さない。</p>
     *
     * <p><b>予定外の荷役は {@code MISROUTED}</b>（不変条件 3 / US28）。どの種別でも
     * 同じで、種別ごとの行き先より先に効く——予定外に運ばれた貨物は、
     * 積んだか降ろしたかより「予定から外れた」ことのほうが重い。</p>
     *
     * @param handlingType 荷役の種別（{@code RECEIVE} / {@code LOAD} /
     *     {@code UNLOAD} / {@code CLAIM}）。<b>handlingms の型は持ち込まない</b>ので
     *     名前で受ける（契約は文字列で運ぶ）
     * @param finalPort 目的港での作業か。旅程を持つ handlingms が判定して載せる
     * @param offRoute 予定ルート外か。同上
     */
    public static TransportStatus afterHandling(String handlingType, boolean finalPort,
            boolean offRoute) {
        if (offRoute) {
            return MISROUTED;
        }
        return switch (handlingType) {
            case "RECEIVE" -> RECEIVED;
            case "LOAD" -> LOADED;
            case "UNLOAD" -> finalPort ? AWAITING_CLAIM : UNLOADED;
            case "CLAIM" -> DELIVERED;
            // **知らない種別を黙って通さない。** 契約に値が増えたのに
            // こちらが追随していない、という状態を素通りさせると、
            // 貨物状態が動かないまま荷役だけが記録される。
            // **業務の判断ではなくこちらの追随漏れ**なので IllegalState。
            default -> throw new IllegalStateException("知らない荷役種別です: " + handlingType);
        };
    }

    /**
     * その状態から<b>手で動かせる先</b>（US17）。<b>判定はここ 1 か所</b>。
     *
     * <p>集約（{@code updateStatusManually}）も読み口（画面の選択肢）もこれを呼ぶ。
     * 別々に持つと、画面が「押しても断られる先」を出す（実際に IT8 のレビューで
     * 出た——例外発生中の追跡に 6 個のボタンが並び、どれを押しても断られた）。</p>
     *
     * <p><b>例外の対応中は動かせない</b>（不変条件 5 の下地）。解決は例外の側の
     * 操作で行い、そのとき例外前の状態へ戻る。</p>
     */
    public static Set<TransportStatus> manualTransitionsFrom(TransportStatus status) {
        if (status == EXCEPTION) {
            return EnumSet.noneOf(TransportStatus.class);
        }
        EnumSet<TransportStatus> allowed = EnumSet.noneOf(TransportStatus.class);
        for (TransportStatus next : values()) {
            if (status.canTransitionTo(next) && next.isSetByHand()) {
                allowed.add(next);
            }
        }
        return allowed;
    }

    /**
     * 追跡管理者が<b>手で選んでよい状態か</b>（US17）。
     *
     * <p><b>誤配は荷役が、例外発生は例外の起票が決める。</b> 手で選べるようにすると、
     * 起きていない誤配を記録できてしまう。とくに例外発生は<b>解決の画面が無いあいだ
     * 行き止まり</b>になる——例外中は手で動かせないので、選んだ人は自分で戻せない。</p>
     *
     * <p>遷移そのものを禁じるのではない（荷役・例外の起票からは入る）。
     * <b>入口を絞る</b>だけである。</p>
     */
    public boolean isSetByHand() {
        return this != MISROUTED && this != EXCEPTION;
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
