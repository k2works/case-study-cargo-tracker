package com.example.bookingms.domain.model;

/** 予約の状態。IT2 で作れるのは仮受付までで、以降の遷移は IT3 以降で実装する。 */
public enum BookingStatus {
    /** 仮受付。経路設計の対象になる。 */
    PRELIMINARY
}
