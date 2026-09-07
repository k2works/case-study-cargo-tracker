package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.ConditionReviewRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.ConditionReviewRespondedEvent;
import com.example.cargotracker.booking.domain.model.events.RouteSpecificationAdjustedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ConditionReviewView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingItineraryQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindConditionReviewsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindRouteConditionQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.RouteConditionView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueryHandler;
import com.example.cargotracker.booking.infrastructure.query.BookingWorklistQueryHandler;
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

/**
 * 条件の調整と差し戻しの投影（US10 / ADR-0009）。
 *
 * <p>決定ごとに検査を対応させる。集約の検査（{@code CargoConditionAdjustmentTest}）は
 * 「集約が何を許すか」を見るもので、<b>投影がどう見えるか</b>は判別しない。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConditionAdjustmentProjectionIT extends AbstractAxonIntegrationTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);
    private static final LocalDate EXTENDED = LocalDate.of(2027, Month.JANUARY, 31);
    private static final Instant AT = Instant.parse("2026-09-06T00:00:00Z");

    @Autowired
    private CargoProjection projection;

    @Autowired
    private BookingQueryHandler queries;

    @Autowired
    private BookingWorklistQueryHandler worklistQueries;

    private static CargoBookedEvent booked(String bookingId) {
        return new CargoBookedEvent(bookingId, "SHP-CA", "JPTYO", "USNYC", DEADLINE,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }

    private String handedOver() {
        String bookingId = "B-CA-" + System.nanoTime();
        projection.on(booked(bookingId));
        projection.on(new RoutingRequestedEvent(bookingId, "sales01"));
        return bookingId;
    }

    private void route(String bookingId) {
        projection.on(new CargoRoutedEvent(bookingId,
                List.of(new CargoRoutedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T00:00:00Z"),
                        Instant.parse("2026-09-24T09:00:00Z"))),
                "routing01", AT));
    }

    private BookingView booking(String bookingId) {
        return queries.handle(new FindBookingQuery(bookingId));
    }

    @Test
    @DisplayName("ADR-0009 決定 3: 条件を調整すると期限が変わり、設計し直しになる")
    void adjustmentUpdatesDeadlineAndReopensDesign() {
        String bookingId = handedOver();
        route(bookingId);
        assertThat(booking(bookingId).routingStatus()).isEqualTo("ROUTED");

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of("SGSIN"), "JPOSA", "routing01", AT));

        BookingView view = booking(bookingId);
        assertThat(view.arrivalDeadline()).isEqualTo(EXTENDED);
        assertThat(view.routingStatus()).isEqualTo("ROUTING_REQUESTED");
    }

    @Test
    @DisplayName("ADR-0009 決定 3: 条件を調整しても確定済みの旅程は残る")
    void adjustmentKeepsTheItinerary() {
        // 消すと「何を組み直すのか」が分からなくなる。再設計で入れ替わるまで残す。
        String bookingId = handedOver();
        route(bookingId);

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of(), null, "routing01", AT));

        assertThat(queries.handle(new FindBookingItineraryQuery(bookingId)).legs())
                .hasSize(1);
    }

    @Test
    @DisplayName("ADR-0009 決定 1: 差し戻しても経路設計の状態は動かない（記録だけが増える）")
    void conditionReviewRecordsWithoutMovingTheStatus() {
        String bookingId = handedOver();

        projection.on(new ConditionReviewRequestedEvent(bookingId,
                "期限内に着ける便がありません", "routing01", AT));

        assertThat(booking(bookingId).routingStatus())
                .as("状態を戻すと、一度も設計していない予約と混ざる")
                .isEqualTo("ROUTING_REQUESTED");
        assertThat(reviewOf(bookingId))
                .isEqualTo(new ConditionReviewView(bookingId,
                        booking(bookingId).bookingNumber(), "期限内に着ける便がありません", AT));
    }

    @Test
    @DisplayName("条件を調整すると、差し戻しの記録は消える（営業の手番が終わる）")
    void adjustmentClearsTheConditionReview() {
        // 残すと営業のダッシュボードに出たままになり、何度も同じ予約を開く。
        String bookingId = handedOver();
        projection.on(new ConditionReviewRequestedEvent(bookingId, "組めません", "routing01", AT));
        assertThat(reviewOf(bookingId)).isNotNull();

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of(), null, "routing01", AT));

        assertThat(reviewOf(bookingId)).isNull();
    }

    private ConditionReviewView reviewOf(String bookingId) {
        return worklistQueries.handle(new FindConditionReviewsQuery(200)).items().stream()
                .filter(item -> item.bookingId().equals(bookingId))
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("調整した除外港と起点が探索の条件として読み出せる（層を生き延びる）")
    void adjustedConditionsSurviveToTheSearch() {
        // **表示のためだけでなく、探索がここから条件を組む。** どこか一層で
        // 落としても集約の検査は緑のままなので、投影から読み直して確かめる。
        String bookingId = handedOver();

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of("SGSIN", "HKHKG"), "JPOSA", "routing01", AT));

        assertThat(queries.handle(new FindRouteConditionQuery(bookingId)))
                .isEqualTo(new RouteConditionView(List.of("SGSIN", "HKHKG"), "JPOSA"));
    }

    @Test
    @DisplayName("調整した条件は予約の読み口からも読める（探索が落ちていても直せる）")
    void adjustedConditionsAreReadableFromTheBooking() {
        // **条件を候補算出の応答だけに載せない。** 探索が落ちている間だけ画面から
        // 条件の欄と差し戻しが消えると、経路設計者は直せる手段を失う。条件が要る
        // のはまさにそのときである（IT6 引き継ぎ 8b）。
        String bookingId = handedOver();

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of("SGSIN", "HKHKG"), "JPOSA", "routing01", AT));

        BookingView view = booking(bookingId);
        assertThat(view.routeExcludeUnLocodes()).containsExactly("SGSIN", "HKHKG");
        assertThat(view.routeDepartFromUnLocode()).isEqualTo("JPOSA");
    }

    @Test
    @DisplayName("調整していない予約は、読み口でも条件が空（null を画面に出さない）")
    void unadjustedBookingHasEmptyConditionsInTheView() {
        BookingView view = booking(handedOver());
        assertThat(view.routeExcludeUnLocodes()).isEmpty();
        assertThat(view.routeDepartFromUnLocode()).isNull();
    }

    @Test
    @DisplayName("調整していない予約の条件は空（IT5 までと同じ探索になる）")
    void unadjustedBookingHasNoConditions() {
        assertThat(queries.handle(new FindRouteConditionQuery(handedOver())))
                .isEqualTo(new RouteConditionView(List.of(), null));
    }

    @Test
    @DisplayName("除外港を外す調整をすると、条件が空に戻る（消し方が無いと絞り込みが積み上がる）")
    void adjustmentCanClearTheConditions() {
        String bookingId = handedOver();
        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of("SGSIN"), "JPOSA", "routing01", AT));

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of(), null, "routing01", AT));

        assertThat(queries.handle(new FindRouteConditionQuery(bookingId)))
                .isEqualTo(new RouteConditionView(List.of(), null));
    }

    @Test
    @DisplayName("投影に無い予約の条件は空（候補算出が先に予約の有無を見る）")
    void unknownBookingHasNoConditions() {
        assertThat(queries.handle(new FindRouteConditionQuery("B-NONE-" + System.nanoTime())))
                .isEqualTo(new RouteConditionView(List.of(), null));
    }

    @Test
    @DisplayName("起点だけを調整しても読み出せる（除外港が空でも条件は残る）")
    void departFromOnlyIsKept() {
        // 「除外港が無ければ条件も無い」と扱うと、起点だけの調整が消える。
        String bookingId = handedOver();

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of(), "JPOSA", "routing01", AT));

        assertThat(queries.handle(new FindRouteConditionQuery(bookingId)))
                .isEqualTo(new RouteConditionView(List.of(), "JPOSA"));
    }

    @Test
    @DisplayName("除外港だけを調整しても読み出せる")
    void excludedPortsOnlyAreKept() {
        String bookingId = handedOver();

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of("SGSIN"), null, "routing01", AT));

        assertThat(queries.handle(new FindRouteConditionQuery(bookingId)))
                .isEqualTo(new RouteConditionView(List.of("SGSIN"), null));
    }

    @Test
    @DisplayName("US10 §4 の対: 協議の結果を返すと営業の受け皿から消え、経路設計者が読める")
    void respondingClearsTheSalesInboxAndIsReadable() {
        // **差し戻しは一方向しか無かった**（IT6 レビュー・IT7 引き継ぎ 2）。
        // 営業は協議を終えても伝える手段が無く、受け皿に出たままだった。
        String bookingId = handedOver();
        projection.on(new ConditionReviewRequestedEvent(bookingId,
                "期限内に着ける便がありません", "routing01", AT));
        assertThat(reviewOf(bookingId)).as("まず営業の受け皿に出る").isNotNull();

        projection.on(new ConditionReviewRespondedEvent(bookingId,
                "荷主が期限を 1 月末まで延ばすことに同意", "sales01", AT));

        assertThat(reviewOf(bookingId))
                .as("返したものが残り続けると、営業は何度も同じ予約を開く")
                .isNull();
        BookingView view = booking(bookingId);
        assertThat(view.conditionReviewResponse())
                .as("経路設計者が読めないと、協議の結果が届かない")
                .isEqualTo("荷主が期限を 1 月末まで延ばすことに同意");
        assertThat(view.conditionReviewRespondedAt()).isEqualTo(AT);
    }

    @Test
    @DisplayName("US10 §4 の対: 返しても差し戻しの理由は消えない（対で読めないと直せない）")
    void respondingKeepsTheOriginalReason() {
        String bookingId = handedOver();
        projection.on(new ConditionReviewRequestedEvent(bookingId, "組めません", "routing01", AT));

        projection.on(new ConditionReviewRespondedEvent(bookingId, "延ばします", "sales01", AT));

        assertThat(booking(bookingId).conditionReviewResponse()).isNotNull();
        // 何を頼まれて何が決まったかが対で読めないと、条件をどう直せばよいか分からない。
        assertThat(queries.handle(new FindBookingQuery(bookingId)))
                .satisfies(v -> assertThat(v.routingStatus()).isEqualTo("ROUTING_REQUESTED"));
    }

    @Test
    @DisplayName("条件を調整すると、協議の結果も消える（営業の手番はもう終わっている）")
    void adjustmentClearsTheResponseToo() {
        String bookingId = handedOver();
        projection.on(new ConditionReviewRequestedEvent(bookingId, "組めません", "routing01", AT));
        projection.on(new ConditionReviewRespondedEvent(bookingId, "延ばします", "sales01", AT));

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of(), null, "routing01", AT));

        assertThat(booking(bookingId).conditionReviewResponse())
                .as("次の差し戻しで前回の返事が残っていると、古い協議が新しい依頼に見える")
                .isNull();
    }
}
