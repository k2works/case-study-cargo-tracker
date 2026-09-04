package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountPreliminaryBookingsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueryHandler;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
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
                LocalDate.of(2026, 12, 1), "GENERAL", new BigDecimal("1200"),
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

        assertThat(queries.handle(new CountPreliminaryBookingsQuery())).isPositive();
    }
}
