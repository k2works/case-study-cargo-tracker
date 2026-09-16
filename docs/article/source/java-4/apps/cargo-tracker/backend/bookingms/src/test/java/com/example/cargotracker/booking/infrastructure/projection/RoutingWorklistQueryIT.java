package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindRoutingWorklistQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueryHandler;
import com.example.cargotracker.booking.infrastructure.query.BookingWorklistQueryHandler;
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

/**
 * 経路設計作業一覧（S30）の読み取りモデル。
 *
 * <p><b>予約の投影全体（{@code CargoProjectionIT}）から分けている。</b> 一覧の絞りと
 * 並び順は「誰がどの順で仕事に取りかかるか」を決めており、投影が値を写せるかとは
 * 別の関心である。まとめて置くと、片方を読むためにもう片方を読み飛ばすことになる。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoutingWorklistQueryIT extends AbstractAxonIntegrationTest {

    @Autowired
    private CargoProjection projection;

    @Autowired
    private BookingQueryHandler queries;

    @Autowired
    private BookingWorklistQueryHandler worklistQueries;

    @Autowired
    private CargoSummaryMapper cargos;

    @Test
    @DisplayName("引き渡すと経路提案中になり、経路設計作業一覧に出る（US06）")
    void appearsInRoutingWorklist() {
        String bookingId = "B-WL-" + System.nanoTime();
        projection.on(booked(bookingId));

        projection.on(new RoutingRequestedEvent(bookingId, "sales01"));

        BookingView view = queries.handle(new FindBookingQuery(bookingId));
        assertThat(view.bookingStatus()).isEqualTo("ROUTE_PROPOSED");
        assertThat(view.routingStatus()).isEqualTo("ROUTING_REQUESTED");
        assertThat(worklistQueries.handle(new FindRoutingWorklistQuery(0, 200, false, "ALL")).items())
                .extracting(BookingView::bookingId).contains(bookingId);
        assertThat(view.routingRequestedAt())
                .as("いつ引き渡されたかが読めないと、期限が遠く放置された案件が"
                        + "一覧の下に埋もれたまま気づかれない（IT3 レビュー R.4）")
                .isNotNull();
    }

    @Test
    @DisplayName("引き渡していない予約に引き渡し日時は入らない")
    void hasNoRoutingRequestedAtBeforeHandover() {
        String bookingId = "B-NOWL-" + System.nanoTime();
        projection.on(booked(bookingId));

        assertThat(queries.handle(new FindBookingQuery(bookingId)).routingRequestedAt())
                .as("受け付けただけで日時が入ると、放置の判断ができない")
                .isNull();
    }

    @Test
    @DisplayName("経路設計作業一覧は誤配が先、そのあと到着期限が近い順に並ぶ")
    void worklistPutsMisroutedFirst() {
        // 並び順を消すとここが赤くなる。誤配は放っておくほど選べる航海が減る。
        //
        // **この組み合わせは本番ではまだ起こらない。** 作業一覧は
        // booking_status = 'ROUTE_PROPOSED' で絞るが、誤配になるのは輸送中で、
        // 遷移表に IN_TRANSIT → ROUTE_PROPOSED は無い。markMisroutedForTest は
        // ROUTE_PROPOSED の行を直接書き換えて、その状態を作っている。
        //
        // したがってこの並び順は**仮置き**である。誤配のときに状態をどう戻すかは
        // US28（IT11）で決める。決めたら、作業一覧の絞りをその決定に合わせ、
        // ここも本番で起こりうる経路で組み直す。
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

        List<String> order = worklistQueries.handle(new FindRoutingWorklistQuery(0, 200, false, "ALL")).items()
                .stream().map(BookingView::bookingId)
                .filter(id -> id.equals(far) || id.equals(near) || id.equals(misrouted))
                .toList();

        assertThat(order).containsExactly(misrouted, near, far);
    }

    @Test
    @DisplayName("輸送中に誤配になった予約も経路設計作業一覧に出る")
    void worklistIncludesMisroutedInTransit() {
        // 誤配の再設計は急ぐ仕事で、S30 が唯一の入口。ここに出ないと
        // 経路設計者は気づく手段を持たない。
        String misrouted = "B-WL-MIT-" + System.nanoTime();
        projection.on(bookedWithDeadline(misrouted, LocalDate.of(2027, Month.MARCH, 1)));
        projection.on(new RoutingRequestedEvent(misrouted, "sales01"));
        cargos.markMisroutedInTransitForTest(misrouted);

        List<String> ids = worklistQueries.handle(new FindRoutingWorklistQuery(0, 200, false, "ALL")).items()
                .stream().map(BookingView::bookingId).toList();

        assertThat(ids)
                .as("誤配は輸送中に起きる。ROUTE_PROPOSED だけで絞ると 1 件も出ない")
                .contains(misrouted);
    }

    @Test
    @DisplayName("誤配だけ・設計待ちだけに絞れる（滞留した誤配が通常の依頼を押し出さない）")
    void canBeNarrowedToOneKind() {
        // **誤配は並びの先頭に来る。** 滞留すると、通常の設計依頼が表示上限の
        // 外に押し出される（IT11 の通しで実測）。上限に当たったことは既に
        // 知らせているので、ここでは**絞る手段**が効くことを見る。
        String awaiting = "B-WL-KIND-AW-" + System.nanoTime();
        String misrouted = "B-WL-KIND-MI-" + System.nanoTime();
        projection.on(bookedWithDeadline(awaiting, LocalDate.of(2027, Month.MAY, 1)));
        projection.on(bookedWithDeadline(misrouted, LocalDate.of(2027, Month.MAY, 2)));
        projection.on(new RoutingRequestedEvent(awaiting, "sales01"));
        projection.on(new RoutingRequestedEvent(misrouted, "sales01"));
        cargos.markMisroutedInTransitForTest(misrouted);

        assertThat(idsOfWorklist("MISROUTED"))
                .as("誤配だけに絞ったのに設計待ちが混ざる")
                .contains(misrouted).doesNotContain(awaiting);
        assertThat(idsOfWorklist("AWAITING"))
                .as("設計待ちだけに絞ったのに誤配が混ざる")
                .contains(awaiting).doesNotContain(misrouted);
        assertThat(idsOfWorklist("ALL"))
                .as("既定は両方。絞りを既定にすると、片方が誰の目にも入らなくなる")
                .contains(awaiting, misrouted);
    }

    private List<String> idsOfWorklist(String kind) {
        return worklistQueries.handle(new FindRoutingWorklistQuery(0, 200, false, kind))
                .items().stream().map(BookingView::bookingId).toList();
    }

    private static CargoBookedEvent bookedWithDeadline(String bookingId, LocalDate deadline) {
        return new CargoBookedEvent(bookingId, "SHP-WL", "JPTYO", "USNYC", deadline,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }

    private static CargoBookedEvent booked(String bookingId) {
        return bookedWithDeadline(bookingId, LocalDate.of(2027, Month.JUNE, 30));
    }
}
