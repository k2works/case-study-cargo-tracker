package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CancellationApprovedEvent;
import com.example.cargotracker.booking.domain.model.events.CancellationRejectedEvent;
import com.example.cargotracker.booking.domain.model.events.CancellationRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries
        .CancellationRequestView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries
        .FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries
        .FindCancellationsOfBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries
        .FindPendingCancellationsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueryHandler;
import com.example.cargotracker.shared.contract.event.CargoCancelledEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * キャンセル申請の投影と読み口（US30 §受入基準 4・10 / IT15 T3）。
 *
 * <p><b>集約の検査は「集約が何を許すか」を見るもの</b>で、承認待ちの一覧や履歴が
 * どう見えるかは判別しない。ここでは実際の PostgreSQL に書いて読み直す。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CancellationProjectionIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-25T02:00:00Z");

    @Autowired
    private CancellationProjection projection;

    @Autowired
    private CargoProjection cargos;

    @Autowired
    private CargoProgressProjection progress;

    @Autowired
    private BookingQueryHandler queries;

    private String booked(String suffix) {
        String bookingId = "B-CX-" + suffix + "-" + System.nanoTime();
        cargos.on(new CargoBookedEvent(bookingId, "SHP-000001", "JPTYO", "USNYC",
                LocalDate.of(2026, Month.DECEMBER, 1), "GENERAL", new BigDecimal("1200"),
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"), 10,
                "キャンセルの貨物 " + suffix, null, null, null, null, "sales01"));
        return bookingId;
    }

    private String requested(String bookingId, String reason) {
        String requestId = "CR-" + System.nanoTime();
        projection.on(new CancellationRequestedEvent(bookingId, requestId, reason,
                "sales01", AT));
        return requestId;
    }

    private CancellationRequestView pendingOf(String bookingId) {
        return queries.handle(new FindPendingCancellationsQuery()).items().stream()
                .filter(item -> bookingId.equals(item.bookingId()))
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("US30 §4: 申請が承認待ちに出て、予約の呼び名と一緒に読める")
    void showsPendingRequests() {
        String bookingId = booked("P1");
        requested(bookingId, "荷主の発注取消");

        var pending = pendingOf(bookingId);
        assertThat(pending).as("書かなければ、追跡管理者は申請が来たことを知る手段が無い")
                .isNotNull();
        assertThat(pending.reason()).isEqualTo("荷主の発注取消");
        assertThat(pending.decisionLabel()).isEqualTo("承認待ち");
        // **予約 ID だけでは、どの貨物の話なのか分からない。**
        assertThat(pending.productName()).contains("キャンセルの貨物");
        assertThat(pending.bookingNumber()).isNotNull();
    }

    @Test
    @DisplayName("承認すると承認待ちから消え、陸揚げ地つきで履歴に残る")
    void recordsTheApproval() {
        String bookingId = booked("A1");
        String requestId = requested(bookingId, "荷主の発注取消");

        projection.on(new CancellationApprovedEvent(bookingId, requestId, "SGSIN",
                "荷主の指定倉庫が近い", "tracker01", AT));

        assertThat(pendingOf(bookingId)).as("判断済みの申請を毎朝読み直させない").isNull();
        assertThat(queries.handle(new FindCancellationsOfBookingQuery(bookingId)).items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.decisionLabel()).isEqualTo("承認済");
                    assertThat(item.dischargeUnLocode()).isEqualTo("SGSIN");
                    assertThat(item.decidedBy()).isEqualTo("tracker01");
                    assertThat(item.decidedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("却下も履歴に残る（行は消さない）")
    void recordsTheRejection() {
        String bookingId = booked("R1");
        String requestId = requested(bookingId, "荷主の発注取消");

        projection.on(new CancellationRejectedEvent(bookingId, requestId,
                "荷受人がすでに手配済み", "tracker01", AT));

        assertThat(pendingOf(bookingId)).isNull();
        assertThat(queries.handle(new FindCancellationsOfBookingQuery(bookingId)).items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.decisionLabel()).isEqualTo("却下済");
                    assertThat(item.decisionReason()).isEqualTo("荷受人がすでに手配済み");
                    assertThat(item.dischargeUnLocode())
                            .as("却下は輸送を続ける判断なので、降ろす港は無い").isNull();
                });
    }

    @Test
    @DisplayName("同じ申請が 2 度届いても 1 行（少なくとも 1 回配送）")
    void isIdempotent() {
        String bookingId = booked("I1");
        String requestId = "CR-" + System.nanoTime();
        var event = new CancellationRequestedEvent(bookingId, requestId, "荷主の発注取消",
                "sales01", AT);

        projection.on(event);
        projection.on(event);

        assertThat(queries.handle(new FindCancellationsOfBookingQuery(bookingId)).items())
                .hasSize(1);
    }

    @Test
    @DisplayName("判断のあとに申請が再配送されても、判断を消さない")
    void keepsTheDecisionWhenTheRequestIsRedelivered() {
        // **上書きすると「承認したはずの申請が承認待ちに戻る」。**
        String bookingId = booked("D1");
        String requestId = "CR-" + System.nanoTime();
        var request = new CancellationRequestedEvent(bookingId, requestId, "荷主の発注取消",
                "sales01", AT);
        projection.on(request);
        projection.on(new CancellationApprovedEvent(bookingId, requestId, "SGSIN", null,
                "tracker01", AT));

        projection.on(request);

        assertThat(pendingOf(bookingId)).isNull();
    }

    @Test
    @DisplayName("US30 §1・§6: キャンセルが予約の状態に出る（記録と読み口は対で出す）")
    void writesCancelledToTheBooking() {
        // **集約がキャンセルになっても、投影に書き手が無ければ営業の一覧は
        // 輸送中のまま残る。** 受け入れテストで実測した欠陥。
        String bookingId = booked("C1");

        progress.on(new CargoCancelledEvent(bookingId, null, "TRACKING_ISSUED", null,
                "荷主の発注取消", "sales01", AT));

        assertThat(queries.handle(new FindBookingQuery(bookingId)).bookingStatus())
                .isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("知らない予約・知らない申請に届いても落ちない（止めない）")
    void toleratesUnknownTargets() {
        String unknown = "B-NONE-" + System.nanoTime();

        progress.on(new CargoCancelledEvent(unknown, null, "TRACKING_ISSUED", null,
                "取り違え", "sales01", AT));
        projection.on(new CancellationApprovedEvent(unknown, "CR-NONE", "SGSIN", null,
                "tracker01", AT));
        projection.on(new CancellationRejectedEvent(unknown, "CR-NONE", "手配済み",
                "tracker01", AT));

        assertThat(pendingOf(unknown)).isNull();
    }
}
