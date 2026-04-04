package com.example.cargotracker.routing.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 航海の 1 区間（レグ）を表す値オブジェクト。
 *
 * <p>出発港から到着港までの単一区間の情報を保持する。
 */
public record VoyageLeg(
    String originLocode,
    String destinationLocode,
    LocalDate departureDate,
    LocalDate arrivalDate
) {
    public VoyageLeg {
        if (originLocode == null || originLocode.isBlank()) {
            throw new IllegalArgumentException("originLocode は null または空にできません");
        }
        if (destinationLocode == null || destinationLocode.isBlank()) {
            throw new IllegalArgumentException("destinationLocode は null または空にできません");
        }
        if (departureDate == null) {
            throw new IllegalArgumentException("departureDate は null にできません");
        }
        if (arrivalDate == null) {
            throw new IllegalArgumentException("arrivalDate は null にできません");
        }
        if (arrivalDate.isBefore(departureDate)) {
            throw new IllegalArgumentException("arrivalDate は departureDate より後でなければなりません");
        }
    }

    /**
     * この区間の所要日数を返す。
     *
     * @return 出発日から到着日までの日数
     */
    public int transitDays() {
        return (int) ChronoUnit.DAYS.between(departureDate, arrivalDate);
    }
}
