package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 経路設計の進み具合（domain-model.md）。予約の状態とは別の軸で動く。
 *
 * <p><b>列挙名を利用者に見せない。</b> 断りのメッセージに {@code NOT_ROUTED} と
 * 出ると、業務担当者には意味が分からず、マニュアルとも照合できない。</p>
 */
public enum RoutingStatus {
    /** 未設計。 */
    NOT_ROUTED("未設計"),
    /** 経路設計を依頼済み。 */
    ROUTING_REQUESTED("設計依頼済み"),
    /** 経路が決まっている。 */
    ROUTED("設計済"),
    /** 予定ルート外の荷役を受けた（誤配）。 */
    MISROUTED("誤配");

    private final String label;

    RoutingStatus(String label) {
        this.label = label;
    }

    /** 利用者に見せる呼び名。 */
    public String label() {
        return label;
    }
}
