package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountBookingsByStatusQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindRoutingWorklistQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueryHandler;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/** 予約の投影と読み取りモデルを実 DB で固定する。 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CargoProjectionIT extends AbstractAxonIntegrationTest {

    @Autowired
    private CargoProjection projection;

    @Autowired
    private ShipperProjection shipperProjection;

    @Autowired
    private BookingQueryHandler queries;

    @Autowired
    private CargoSummaryMapper cargos;

    private static CargoBookedEvent booked(String bookingId, String shipperId, String product) {
        return new CargoBookedEvent(bookingId, shipperId, "JPTYO", "USNYC",
                LocalDate.of(2026, Month.DECEMBER, 1), "GENERAL", new BigDecimal("1200"),
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"),
                10, product, null, null, null, null, "sales01");
    }

    @Test
    @DisplayName("荷主名を非正規化して持つ")
    void denormalizesShipperName() {
        // 一覧が JOIN しないため（data-model.md）。
        String shipperId = "SHP-P-" + System.nanoTime();
        shipperProjection.on(new ShipperRegisteredEvent(shipperId, "INDIVIDUAL", "山田商事",
                shipperId + "@example.com", null, null, null, null));
        String bookingId = "B-P-" + System.nanoTime();

        projection.on(booked(bookingId, shipperId, "自動車部品"));

        BookingView view = queries.handle(new FindBookingQuery(bookingId));
        assertThat(view.shipperName()).isEqualTo("山田商事");
        assertThat(view.bookingNumber()).startsWith("B-");
        assertThat(view.bookingStatus()).isEqualTo("PRELIMINARY");
        assertThat(view.lengthCm()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("荷主の投影が遅れていても予約は落とさない")
    void keepsBookingWhenShipperIsMissing() {
        // 予約そのものは受け付けられている。荷主が見つからないことを理由に
        // 予約を落とすと、受け付けたのに一覧に出ない予約ができる。
        String bookingId = "B-NOSHIPPER-" + System.nanoTime();

        projection.on(booked(bookingId, "SHP-UNKNOWN-" + System.nanoTime(), "部品"));

        BookingView view = queries.handle(new FindBookingQuery(bookingId));
        assertThat(view).isNotNull();
        assertThat(view.shipperName()).isNull();
    }

    @Test
    @DisplayName("同じイベントが 2 度届いても行は増えない")
    void isIdempotent() {
        // 全体件数の差では見ない。他のテストが同じ DB に行を足すので、原因でない
        // テストの影響で落ちる。この予約が 1 行だけあることを直接見る。
        String bookingId = "B-DUP-" + System.nanoTime();

        projection.on(booked(bookingId, "SHP-X", "部品"));
        String firstNumber = queries.handle(new FindBookingQuery(bookingId)).bookingNumber();
        projection.on(booked(bookingId, "SHP-X", "部品"));

        assertThat(queries.handle(new FindBookingQuery(bookingId)).bookingNumber())
                .as("2 度目で予約番号が振り直されると、書類と一覧が食い違う")
                .isEqualTo(firstNumber);
    }

    @Test
    @DisplayName("見つからない予約は null を返す")
    void returnsNullForUnknownBooking() {
        assertThat(queries.handle(new FindBookingQuery("B-NOT-EXIST"))).isNull();
    }

    @Test
    @DisplayName("一覧は終了したものを既定で外す")
    void excludesFinishedByDefault() {
        String bookingId = "B-SETTLED-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-X", "精算済の荷"));
        cargos.markSettledForTest(bookingId);

        BookingListView visible = queries.handle(new FindBookingsQuery(0, 200, false));
        BookingListView all = queries.handle(new FindBookingsQuery(0, 200, true));

        assertThat(visible.items()).noneMatch(i -> i.bookingId().equals(bookingId));
        assertThat(all.items()).anyMatch(i -> i.bookingId().equals(bookingId));
        assertThat(all.total()).isGreaterThan(visible.total());
    }

    @Test
    @DisplayName("ページの大きさは上限で丸める")
    void clampsPageSize() {
        // 上限を外すと、1 回の問い合わせで全件を引かれて一覧が固まる。
        assertThat(queries.handle(new FindBookingsQuery(-1, 10_000, true)).items().size())
                .isLessThanOrEqualTo(200);
    }

    @Test
    @DisplayName("仮受付の件数を数える")
    void countsPreliminary() {
        projection.on(booked("B-CNT-" + System.nanoTime(), "SHP-X", "数える荷"));

        assertThat(queries.handle(new CountBookingsByStatusQuery("PRELIMINARY"))).isPositive();
        assertThat(queries.handle(new CountBookingsByStatusQuery("CANCELLED")))
                .as("状態を引数で受けるので、別の状態でも数えられる")
                .isNotNull();
    }

    @Test
    @DisplayName("一覧は到着期限が近い順に並ぶ")
    void ordersByArrivalDeadline() {
        // UI 設計「一覧の既定条件」。ORDER BY を丸ごと消しても、除外の検査だけでは
        // 緑のままになる。営業が上から片付ける前提が崩れる。
        String stamp = String.valueOf(System.nanoTime());
        String far = "B-ORDER-FAR-" + stamp;
        String near = "B-ORDER-NEAR-" + stamp;

        projection.on(new CargoBookedEvent(far, "SHP-ORDER", "JPTYO", "USNYC",
                LocalDate.of(2027, Month.DECEMBER, 1), "GENERAL", new BigDecimal("1"),
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), 1,
                "遠い期限-" + stamp, null, null, null, null, "sales01"));
        projection.on(new CargoBookedEvent(near, "SHP-ORDER", "JPTYO", "USNYC",
                LocalDate.of(2027, Month.JANUARY, 1), "GENERAL", new BigDecimal("1"),
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), 1,
                "近い期限-" + stamp, null, null, null, null, "sales01"));

        List<String> ids = queries.handle(new FindBookingsQuery(0, 200, true)).items().stream()
                .map(BookingView::bookingId)
                .filter(id -> id.endsWith(stamp))
                .toList();

        assertThat(ids)
                .as("期限が近いものが先。あとから登録しても順序は期限で決まる")
                .containsExactly(near, far);
    }

    @Test
    @DisplayName("引き渡すと経路提案中になり、経路設計作業一覧に出る（US06）")
    void appearsInRoutingWorklist() {
        String bookingId = "B-WL-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-WL", "自動車部品"));

        projection.on(new RoutingRequestedEvent(bookingId, "sales01"));

        BookingView view = queries.handle(new FindBookingQuery(bookingId));
        assertThat(view.bookingStatus()).isEqualTo("ROUTE_PROPOSED");
        assertThat(view.routingStatus()).isEqualTo("ROUTING_REQUESTED");
        assertThat(queries.handle(new FindRoutingWorklistQuery(0, 200, false)).items())
                .extracting(BookingView::bookingId).contains(bookingId);
    }

    @Test
    @DisplayName("経路設計作業一覧は誤配が先、そのあと到着期限が近い順に並ぶ")
    void worklistPutsMisroutedFirst() {
        // 並び順を消すとここが赤くなる。誤配は放っておくほど選べる航海が減る。
        String far = "B-WL-FAR-" + System.nanoTime();
        String near = "B-WL-NEAR-" + System.nanoTime();
        String misrouted = "B-WL-MIS-" + System.nanoTime();
        projection.on(bookedWithDeadline(far, LocalDate.of(2027, Month.JANUARY, 31)));
        projection.on(bookedWithDeadline(near, LocalDate.of(2026, Month.OCTOBER, 1)));
        projection.on(bookedWithDeadline(misrouted, LocalDate.of(2027, Month.DECEMBER, 31)));
        for (String id : List.of(far, near, misrouted)) {
            projection.on(new RoutingRequestedEvent(id, "sales01"));
        }
        cargos.markMisroutedForTest(misrouted);

        List<String> order = queries.handle(new FindRoutingWorklistQuery(0, 200, false)).items()
                .stream().map(BookingView::bookingId)
                .filter(id -> id.equals(far) || id.equals(near) || id.equals(misrouted))
                .toList();

        assertThat(order).containsExactly(misrouted, near, far);
    }

    private static CargoBookedEvent bookedWithDeadline(String bookingId, LocalDate deadline) {
        return new CargoBookedEvent(bookingId, "SHP-WL", "JPTYO", "USNYC", deadline,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }
}
