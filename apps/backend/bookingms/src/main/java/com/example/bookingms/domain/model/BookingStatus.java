package com.example.bookingms.domain.model;

/** 予約の状態。荷主との約束がどこまで進んだかを表す（[ADR-020]）。 */
public enum BookingStatus {
    /** 仮受付。経路設計の対象になる。 */
    PRELIMINARY,

    /**
     * 経路を提示できる状態（US09 / US11）。
     *
     * <p>確定（`CONFIRMED`）ではない。<strong>確定は荷主の合意を経た別の作業</strong>（US13）であり、
     * 経路が決まっただけで確定にすると、荷主が見ていない条件で契約が成立したことになる。
     */
    ROUTE_PROPOSED
}
