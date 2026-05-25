package com.example.bookingms.domain.model;

import java.time.LocalDate;

/**
 * 経路仕様（出発地・目的地・到着期限）。値オブジェクト。
 *
 * <p>出発地と目的地は UN/LOCODE 文字列で表現する。</p>
 */
public record RouteSpecification(
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline
) {
}
