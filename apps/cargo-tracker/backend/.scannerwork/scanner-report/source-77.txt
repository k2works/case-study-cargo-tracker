package com.example.cargotracker.booking.domain.model.valueobjects;

/** 経路設計の進み具合（domain-model.md）。予約の状態とは別の軸で動く。 */
public enum RoutingStatus {
    /** 未設計。 */
    NOT_ROUTED,
    /** 経路設計を依頼済み。 */
    ROUTING_REQUESTED,
    /** 経路が決まっている。 */
    ROUTED,
    /** 予定ルート外の荷役を受けた（誤配）。 */
    MISROUTED
}
