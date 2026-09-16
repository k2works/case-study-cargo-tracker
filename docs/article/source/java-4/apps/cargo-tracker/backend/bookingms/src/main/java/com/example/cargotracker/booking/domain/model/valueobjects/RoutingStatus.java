package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 経路設計の進み具合（domain-model.md）。予約の状態とは別の軸で動く。
 *
 * <p><b>列挙名を利用者に見せない。</b> 断りのメッセージに {@code NOT_ROUTED} と
 * 出ると、業務担当者には意味が分からず、マニュアルとも照合できない。</p>
 */
public enum RoutingStatus {
    /** 未設計。 */
    NOT_ROUTED("未設定"),
    /** 経路設計を依頼済み。 */
    ROUTING_REQUESTED("設計依頼中"),
    /** 経路が決まっている。 */
    ROUTED("設定済"),
    /** 予定ルート外の荷役を受けた（誤配）。 */
    MISROUTED("誤配（再設計が要る）");

    private final String label;

    RoutingStatus(String label) {
        this.label = label;
    }

    /** 利用者に見せる呼び名。 */
    public String label() {
        return label;
    }

    /**
     * 条件の見直しを営業へ差し戻せるか（US10 §受入基準 4 / ADR-0009 決定 2）。
     *
     * <p><b>誤配（{@code MISROUTED}）は含めない。</b> 誤配は「荷物が経路から外れた」
     * ことで、条件では組めないこととは別である。差し戻せると、荷物が動いている予約が
     * 営業の手番に見える。誤配からの再設計は US28（IT11）が持つ。</p>
     *
     * <p>経路が決まっている（{@code ROUTED}）予約も差し戻せない。組めているのだから
     * 条件の見直しは要らず、変えたいなら先に条件を調整する
     * （{@code AdjustRouteSpecificationCommand}）。</p>
     */
    public boolean canRequestConditionReview() {
        return this == ROUTING_REQUESTED;
    }

    /**
     * 経路の条件を調整できるか（US10 / ADR-0009 決定 3）。
     *
     * <p>引き渡してから経路が決まるまでの間と、決まったあとに変えたいときに開く。</p>
     *
     * <p><b>誤配（{@code MISROUTED}）は含めない。</b> 調整は routingStatus を
     * {@code ROUTING_REQUESTED} に戻すので、誤配の記録が消える。誤配からの再設計は
     * US28（IT11）が持つ判断で、そこを先に縛らない。</p>
     */
    public boolean canAdjustRouteSpecification() {
        return this == ROUTING_REQUESTED || this == ROUTED;
    }

    /**
     * 経路を確定できるか（US09）。
     *
     * <p>引き渡していない予約に経路が付くと、営業の知らないところで設計が進む。
     * <b>誤配（{@code MISROUTED}）からの再設計は許す</b>（US28）。</p>
     *
     * <p><b>差し戻せるか（{@link #canRequestConditionReview()}）とは別の判断。</b>
     * あちらは誤配を許さない。同じ述語で出し分けると誤配で押せて 422 になる。</p>
     */
    public boolean canAssignRoute() {
        return this == ROUTING_REQUESTED || this == MISROUTED;
    }

    /**
     * 荷主へ通知できるか（US12）。
     *
     * <p>通知は「この経路で運びます」と伝えること。経路が無いまま伝えると、荷主は
     * 何も確認できない。<b>予約の状態も別に見る</b>（遷移表に無い後退を防ぐ）。</p>
     */
    public boolean canNotifyShipper() {
        return this == ROUTED;
    }
}
