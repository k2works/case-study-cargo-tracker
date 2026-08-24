package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoRestoration;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoStatus;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

/**
 * 予約の入口テストが共有する貨物。
 *
 * <p>入口を 2 つに割った（{@link CargoBookingController} / {@link CargoRoutingController}）
 * とき、この組み立てを両方に写すと、集約の作り方が変わったときに<strong>片方だけ直る</strong>。
 * 手番が違っても、通ってきた予約は同じものである。
 */
final class BookingTestCargoes {

    private BookingTestCargoes() {
    }

    static Cargo booked() {
        return CargoRestoration.restore(1L, BookingId.of("BKG-2026000001"), 1L, CargoStatus.preliminary(),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(Location.of("JPTYO", "Tokyo"),
                        Location.of("USLAX", "Los Angeles"), LocalDate.of(2027, Month.SEPTEMBER, 1),
                        LocalDate.of(2027, Month.SEPTEMBER, 20)));
    }

    /** 経路設計者へ引き渡し済みの予約。 */
    static Cargo requested() {
        return booked().requestRouting();
    }

    /** 経路が決まった予約（[ADR-020] 決定 2）。 */
    static Cargo routed() {
        return requested().assignItinerary(
                CargoItinerary.of(List.of(Leg.of(VoyageNumber.of("V0100"),
                        Location.of("JPTYO", "Tokyo"), Location.of("USLAX", "Los Angeles"),
                        Instant.parse("2027-09-02T09:00:00Z"),
                        Instant.parse("2027-09-15T09:00:00Z")))),
                ZoneId.of("America/Los_Angeles"));
    }

    /** 荷主へ経路を通知済みの予約。 */
    static Cargo notified() {
        return routed().notifyShipper(Instant.parse("2026-08-22T02:00:00Z"), "sales01");
    }
}
