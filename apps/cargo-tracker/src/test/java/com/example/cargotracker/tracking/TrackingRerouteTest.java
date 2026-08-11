package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.TrackingPort;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.CargoRouteAssignments;
import com.example.cargotracker.support.CargoFixture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 追跡が持つ目的地・推定到着日の写し（ADR-012 / IT8 の C13）。
 *
 * <p>Tracking → Booking の問い合わせ（{@code CargoArrivalEstimates}）を廃止し、
 * <strong>発行時に渡す + 経路変更で追随する</strong>形に変えた。
 *
 * <p><strong>片方だけでは足りない。</strong> 発行時の受け渡しだけだと、経路を変えても
 * 古い到着予定が残る。本テストは両方を確かめる。
 */
@DisplayName("ADR-012 追跡が持つ目的地の写し")
class TrackingRerouteTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrackingPort trackingPort;

    @Autowired
    private CargoRouteAssignments cargoRouteAssignments;

    /** 業務のクロック。**JVM 既定の now() を使うと CI（UTC）でだけずれる。** */
    @Autowired
    private java.time.Clock clock;

    private UUID 確定済みの予約() {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("山田物産株式会社")
                .arrivalDeadline(LocalDate.now(clock).plusDays(60))
                .status("ROUTE_PROPOSED", "NOT_ROUTED")
                .insert();
        UUID bookingId = cargo.bookingId();
        return bookingId;
    }

    /** 発行時に目的地と推定到着日を受け取る。**Booking へ問い合わせない。** */
    @Test
    void 発行時に目的地と推定到着日を受け取る() {
        UUID bookingId = 確定済みの予約();
        LocalDate arrival = LocalDate.now(clock).plusDays(20);

        String number = trackingPort.issue(bookingId, "USLAX", arrival);

        var row = jdbcTemplate.queryForMap(
                "SELECT destination_unlocode, estimated_arrival_date"
                        + " FROM tracking_activity WHERE tracking_number = ?", number);
        assertThat(row).containsEntry("destination_unlocode", "USLAX");
        assertThat(((java.sql.Date) row.get("estimated_arrival_date")).toLocalDate())
                .isEqualTo(arrival);
    }

    /**
     * 経路が変わったら追随する（{@code CargoRoutedEvent} の購読）。
     *
     * <p><strong>発行時の受け渡しだけを入れると、ここが古いまま残る。</strong>
     */
    @Test
    void 経路が変わると推定到着日が追随する() {
        UUID bookingId = 確定済みの予約();
        LocalDate stale = LocalDate.now(clock).plusDays(20);
        String number = trackingPort.issue(bookingId, "USLAX", stale);

        var loadTime = clock.instant().plus(java.time.Duration.ofDays(3));
        var unloadTime = clock.instant().plus(java.time.Duration.ofDays(35));
        var result = cargoRouteAssignments.assign(bookingId, List.of(
                new CargoRouteAssignments.LegAssignment(
                        "V0099", "JPOSA", "USLAX", loadTime, unloadTime)));

        assertThat(result).isEqualTo(CargoRouteAssignments.AssignmentResult.ASSIGNED);

        // **「古くない」では判別できない。** 旅程と無関係な値を書く実装でも通る。
        // 最終区間の荷降予定日そのものと突き合わせる
        var row = jdbcTemplate.queryForMap(
                "SELECT estimated_arrival_date FROM tracking_activity WHERE tracking_number = ?",
                number);
        assertThat(((java.sql.Date) row.get("estimated_arrival_date")).toLocalDate())
                .isEqualTo(unloadTime.atZone(clock.getZone()).toLocalDate())
                .isNotEqualTo(stale);
    }

    /**
     * 追跡番号がまだ無い予約への経路割り当ては<strong>取りこぼしではない</strong>。
     *
     * <p>経路の割り当ては発行より前に起きる。そのときの目的地は発行時に渡される。
     * ここを「取りこぼし」として数えると、正常な流れが警告になり続ける。
     */
    @Test
    void 発行前の経路割り当ては取りこぼしにしない() {
        UUID bookingId = 確定済みの予約();

        var result = cargoRouteAssignments.assign(bookingId, List.of(
                new CargoRouteAssignments.LegAssignment(
                        "V0100", "JPOSA", "USLAX",
                        clock.instant().plus(java.time.Duration.ofDays(3)),
                        clock.instant().plus(java.time.Duration.ofDays(30)))));

        assertThat(result).isEqualTo(CargoRouteAssignments.AssignmentResult.ASSIGNED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tracking_activity WHERE booking_id = ?",
                Integer.class, bookingId)).isZero();
    }
}
