package com.example.cargotracker.booking.domain.model.valueobjects;

import java.time.LocalDate;

/**
 * 予約の航海区間を表す値オブジェクト。
 * AssignedRoute が経路全体のサマリを持つのに対し、BookingLeg は個々の区間詳細を表現する。
 * String 型フィールドを使用（既存 AssignedRoute と一貫したアプローチ）。
 */
public record BookingLeg(
        String voyageNumber,
        String originLocode,
        String destinationLocode,
        LocalDate departureDate,
        LocalDate arrivalDate,
        int legOrder
) {
    public BookingLeg {
        if (voyageNumber == null || voyageNumber.isBlank())
            throw new IllegalArgumentException("航海番号は必須です");
        if (originLocode == null || originLocode.isBlank())
            throw new IllegalArgumentException("出発港は必須です");
        if (destinationLocode == null || destinationLocode.isBlank())
            throw new IllegalArgumentException("到着港は必須です");
        if (departureDate == null)
            throw new IllegalArgumentException("出発日は必須です");
        if (arrivalDate == null)
            throw new IllegalArgumentException("到着日は必須です");
        if (!arrivalDate.isAfter(departureDate))
            throw new IllegalArgumentException("到着日は出発日より後である必要があります");
        if (legOrder < 0)
            throw new IllegalArgumentException("区間順序は 0 以上である必要があります");
    }
}
