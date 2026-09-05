package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoSpecificationUpdatedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountBookingsByStatusQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingItineraryQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingRevisionsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ItineraryLegView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.RevisionView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindRoutingWorklistQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueryHandler;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
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
    private com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper
            attentionItems;

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
        assertThat(view.routingRequestedAt())
                .as("いつ引き渡されたかが読めないと、期限が遠く放置された案件が"
                        + "一覧の下に埋もれたまま気づかれない（IT3 レビュー R.4）")
                .isNotNull();
    }

    @Test
    @DisplayName("引き渡していない予約に引き渡し日時は入らない")
    void hasNoRoutingRequestedAtBeforeHandover() {
        String bookingId = "B-NOWL-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-NOWL", "自動車部品"));

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

        List<String> order = queries.handle(new FindRoutingWorklistQuery(0, 200, false)).items()
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

        List<String> ids = queries.handle(new FindRoutingWorklistQuery(0, 200, false)).items()
                .stream().map(BookingView::bookingId).toList();

        assertThat(ids)
                .as("誤配は輸送中に起きる。ROUTE_PROPOSED だけで絞ると 1 件も出ない")
                .contains(misrouted);
    }

    private static CargoBookedEvent bookedWithDeadline(String bookingId, LocalDate deadline) {
        return new CargoBookedEvent(bookingId, "SHP-WL", "JPTYO", "USNYC", deadline,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }

    private static CargoSpecificationUpdatedEvent corrected(String bookingId) {
        return new CargoSpecificationUpdatedEvent(bookingId, "JPTYO", "GBLON",
                LocalDate.of(2026, Month.DECEMBER, 20), "HAZARDOUS",
                new BigDecimal("1500.00"), new BigDecimal("130.00"), new BigDecimal("80.00"),
                new BigDecimal("100.00"), 12, "塗料", "3", "UN1263", null, null, "sales02",
                java.time.Instant.parse("2026-09-05T00:00:00Z"));
    }

    @Test
    @DisplayName("US32: 修正が投影に反映され、最終更新が残る")
    void appliesSpecificationUpdate() {
        String bookingId = "B-UPD-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-UPD", "自動車部品"));

        projection.on(corrected(bookingId));

        BookingView view = queries.handle(new FindBookingQuery(bookingId));
        assertThat(view.productName()).isEqualTo("塗料");
        assertThat(view.cargoType()).isEqualTo("HAZARDOUS");
        assertThat(view.hazardImoClass()).isEqualTo("3");
        assertThat(view.destinationUnLocode()).isEqualTo("GBLON");
        assertThat(view.arrivalDeadline()).isEqualTo(LocalDate.of(2026, Month.DECEMBER, 20));
        assertThat(view.updatedAt()).isNotNull();
        assertThat(view.updatedBy()).isEqualTo("sales02");
        assertThat(view.bookingStatus())
                .as("修正で状態は動かない。仮受付のまま内容だけが差し替わる")
                .isEqualTo("PRELIMINARY");
    }

    @Test
    @DisplayName("US32: 一般貨物へ直すと危険物の申告は残らない")
    void clearsExtrasWhenTypeChanges() {
        // 上書きせずに残すと、一般貨物なのに危険物申告が付いた行になり、
        // 経路設計が「危険物対応の航海だけ」に絞るべきか判断できない。
        String bookingId = "B-UPD2-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-UPD", "自動車部品"));
        projection.on(corrected(bookingId));

        projection.on(new CargoSpecificationUpdatedEvent(bookingId, "JPTYO", "USNYC",
                LocalDate.of(2026, Month.DECEMBER, 1), "GENERAL",
                new BigDecimal("1200.00"), new BigDecimal("120.00"), new BigDecimal("80.00"),
                new BigDecimal("100.00"), 10, "自動車部品", null, null, null, null, "sales02",
                java.time.Instant.parse("2026-09-05T01:00:00Z")));

        BookingView view = queries.handle(new FindBookingQuery(bookingId));
        assertThat(view.cargoType()).isEqualTo("GENERAL");
        assertThat(view.hazardImoClass()).isNull();
        assertThat(view.hazardUnNumber()).isNull();
    }

    @Test
    @DisplayName("投影に無い予約の修正は黙って捨てない")
    void recordsUpdateWithoutRow() {
        String bookingId = "B-NOROW-" + System.nanoTime();

        projection.on(corrected(bookingId));

        assertThat(queries.handle(new FindBookingQuery(bookingId)))
                .as("行を作らない。受け付けを経ていない予約が投影にだけ生まれる")
                .isNull();
        assertThat(attentionItems.findOpenByRole("ROLE_SALES"))
                .as("黙って捨てると、直したのに反映されないことが誰にも見えない")
                .anySatisfy(item -> assertThat(item.targetId()).isEqualTo(bookingId));
    }

    @Test
    @DisplayName("R.2: 何を変えたかが読める（記録と読み口を対で出す）")
    void recordsWhatChanged() {
        String bookingId = "B-REV-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-REV", "自動車部品"));

        projection.on(corrected(bookingId));

        List<RevisionView> items =
                queries.handle(new FindBookingRevisionsQuery(bookingId)).items();
        assertThat(items).isNotEmpty();
        assertThat(items).allSatisfy(item -> {
            assertThat(item.updatedBy()).isEqualTo("sales02");
            assertThat(item.updatedAt()).isEqualTo(Instant.parse("2026-09-05T00:00:00Z"));
        });
        assertThat(items).extracting(RevisionView::label)
                .contains("目的地", "希望着日", "貨物種別", "品名", "IMO クラス", "国連番号");
        assertThat(items).filteredOn(item -> item.label().equals("目的地"))
                .extracting(RevisionView::before, RevisionView::after)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("USNYC", "GBLON"));
        // 変えていない項目は出さない。全項目が並ぶと「何を変えたか」が読めない。
        assertThat(items).extracting(RevisionView::label).doesNotContain("出発地");
    }

    @Test
    @DisplayName("R.2: 修正イベントを読み直しても履歴は増えない")
    void revisionsAreIdempotentOnReplay() {
        String bookingId = "B-REV2-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-REV2", "自動車部品"));

        projection.on(corrected(bookingId));
        int afterFirst = queries.handle(new FindBookingRevisionsQuery(bookingId)).items().size();
        projection.on(corrected(bookingId));

        // 2 度目は投影がもう新しい値なので差分は出ない。1 度目の行も増えない。
        assertThat(queries.handle(new FindBookingRevisionsQuery(bookingId)).items())
                .hasSize(afterFirst);
    }

    @Test
    @DisplayName("R.2: 一度も直していない予約の履歴は空（404 にしない）")
    void noRevisionsWhenNeverUpdated() {
        String bookingId = "B-REV3-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-REV3", "自動車部品"));

        assertThat(queries.handle(new FindBookingRevisionsQuery(bookingId)).items()).isEmpty();
    }

    private static final Instant ROUTED_AT = Instant.parse("2026-09-06T00:00:00Z");

    private static CargoRoutedEvent routed(String bookingId, List<CargoRoutedEvent.Leg> legs) {
        return new CargoRoutedEvent(bookingId, legs, "routing01", ROUTED_AT);
    }

    private static CargoRoutedEvent.Leg leg(String voyage, String from, String to,
            String load, String unload) {
        return new CargoRoutedEvent.Leg(voyage, from, to,
                Instant.parse(load), Instant.parse(unload));
    }

    @Test
    @DisplayName("US09: 経路が投影され、区間が積む順に読める")
    void projectsItinerary() {
        String bookingId = "B-RT-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-RT", "自動車部品"));
        projection.on(new RoutingRequestedEvent(bookingId, "sales01"));

        projection.on(routed(bookingId, List.of(
                leg("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-16T00:00:00Z"),
                leg("V-2", "SGSIN", "USNYC", "2026-09-17T00:00:00Z", "2026-09-25T00:00:00Z"))));

        // **行を丸ごと比べる。** 列ごとに積み上げると、区間に属性が増えたときに
        // 増えた分の検査が抜ける。
        assertThat(queries.handle(new FindBookingItineraryQuery(bookingId)).legs())
                .containsExactly(
                        new ItineraryLegView(1, "V-1", "JPTYO", "SGSIN",
                                Instant.parse("2026-09-10T00:00:00Z"),
                                Instant.parse("2026-09-16T00:00:00Z")),
                        new ItineraryLegView(2, "V-2", "SGSIN", "USNYC",
                                Instant.parse("2026-09-17T00:00:00Z"),
                                Instant.parse("2026-09-25T00:00:00Z")));

        BookingView view = queries.handle(new FindBookingQuery(bookingId));
        assertThat(view.routingStatus()).isEqualTo("ROUTED");
        // 荷主に通知するまでは提案中（US12）。ここが CONFIRMED になってはいけない。
        assertThat(view.bookingStatus()).isEqualTo("ROUTE_PROPOSED");
    }

    @Test
    @DisplayName("US09: 設計し直すと区間は入れ替わる（古い区間が残らない）")
    void replacesLegsOnReassignment() {
        String bookingId = "B-RT2-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-RT2", "自動車部品"));
        projection.on(new RoutingRequestedEvent(bookingId, "sales01"));
        projection.on(routed(bookingId, List.of(
                leg("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-16T00:00:00Z"),
                leg("V-2", "SGSIN", "USNYC", "2026-09-17T00:00:00Z", "2026-09-25T00:00:00Z"))));

        // 短い旅程に置き換える。足すだけだと、行かないはずの SGSIN が残る。
        projection.on(routed(bookingId, List.of(
                leg("V-9", "JPTYO", "USNYC", "2026-09-11T00:00:00Z", "2026-09-24T00:00:00Z"))));

        assertThat(queries.handle(new FindBookingItineraryQuery(bookingId)).legs())
                .containsExactly(new ItineraryLegView(1, "V-9", "JPTYO", "USNYC",
                        Instant.parse("2026-09-11T00:00:00Z"),
                        Instant.parse("2026-09-24T00:00:00Z")));
    }

    @Test
    @DisplayName("US09: 同じイベントを読み直しても区間は増えない（リプレイの冪等性）")
    void itineraryIsIdempotentOnReplay() {
        String bookingId = "B-RT3-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-RT3", "自動車部品"));
        projection.on(new RoutingRequestedEvent(bookingId, "sales01"));
        CargoRoutedEvent event = routed(bookingId, List.of(
                leg("V-1", "JPTYO", "USNYC", "2026-09-10T00:00:00Z", "2026-09-24T00:00:00Z")));

        projection.on(event);
        projection.on(event);

        assertThat(queries.handle(new FindBookingItineraryQuery(bookingId)).legs()).hasSize(1);
    }

    @Test
    @DisplayName("US09: 投影に無い予約の経路は要確認一覧に残る（黙らない）")
    void recordsAttentionWhenRoutingUnknownBooking() {
        String bookingId = "B-RT4-" + System.nanoTime();

        projection.on(routed(bookingId, List.of(
                leg("V-1", "JPTYO", "USNYC", "2026-09-10T00:00:00Z", "2026-09-24T00:00:00Z"))));

        assertThat(attentionItems.findOpenByRole("ROLE_ROUTING"))
                .anyMatch(item -> item.targetId().equals(bookingId)
                        && item.reason().equals("経路の対象が投影に無い"));
    }

    @Test
    @DisplayName("US09: 経路が決まっていない予約の旅程は空（404 にしない）")
    void noItineraryWhenNotRouted() {
        String bookingId = "B-RT5-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-RT5", "自動車部品"));

        assertThat(queries.handle(new FindBookingItineraryQuery(bookingId)).legs()).isEmpty();
    }
}
