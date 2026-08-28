package com.example.bookingms.domain.model.aggregates;

import static com.example.bookingms.domain.model.aggregates.CargoFixtures.ROUTE;
import static com.example.bookingms.domain.model.aggregates.CargoFixtures.specification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.RoutingStatus;
import com.example.bookingms.domain.model.valueobjects.VoyageNumber;

/**
 * 経路が決まってから追跡番号を渡すまでの貨物予約。
 *
 * <p>受け付けたとき（{@link CargoTest}）と分けたのは、集約が別の局面に入るからである。
 * 経路が入ると期限・端点の検査が働き、確定すると変えられないものが増える。
 */
@DisplayName("貨物予約（経路の割り当てから確定まで）")
class CargoItineraryLifecycleTest {

    /**
     * 経路の割り当て（US09・[ADR-020]）。
     *
     * <p>ADR-020 は決定を 6 つ持つ。**決定の数だけ検査を用意する**（文章だけの決定は守られない）。
     */
    @Nested
    @DisplayName("経路を割り当てるとき")
    class WhenItineraryAssigned {

        private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
        private static final Location BUSAN = Location.of("KRPUS", "Busan");
        private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
        private static final ZoneId LA = ZoneId.of("America/Los_Angeles");

        /** 訂正後の到着期限。 */
        private static final LocalDate REVISED_DEADLINE = LocalDate.of(2026, Month.OCTOBER, 10);

        /** 期限の「今日」を決める時刻。テストと実装で同じ時刻源を共有する。 */
        private static final java.time.Clock FIXED_CLOCK =
                java.time.Clock.fixed(Instant.parse("2026-08-22T02:00:00Z"),
                        java.time.ZoneOffset.UTC);

        private static CargoItinerary itinerary(Location from, Location to, String arrival) {
            return CargoItinerary.of(List.of(Leg.of(VoyageNumber.of("V0100"), from, to,
                    Instant.parse("2026-09-01T09:00:00Z"), Instant.parse(arrival))));
        }

        private static CargoItinerary valid() {
            return itinerary(TOKYO, LOS_ANGELES, "2026-09-15T12:00:00Z");
        }

        private static Cargo requested() {
            return Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE)
                    .requestRouting();
        }

        /** 決定 2: 2 つの状態が動く。確定にはしない。 */
        @Test
        @DisplayName("経路の状態は ROUTED、予約の状態は ROUTE_PROPOSED になる")
        void movesBothStatuses() {
            Cargo assigned = requested().assignItinerary(valid(), LA);

            assertThat(assigned.routingStatus()).isEqualTo(RoutingStatus.ROUTED);
            // 確定は荷主の合意を経た別の作業（US13）。経路が決まっただけで確定にすると、
            // 荷主が見ていない条件で契約が成立したことになる
            assertThat(assigned.bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
            assertThat(assigned.itinerary()).contains(valid());
        }

        /** 決定 1: ROUTING_REQUESTED からのみ。 */
        @Test
        @DisplayName("引き渡されていない予約には割り当てられない")
        void rejectsAssignmentBeforeRoutingRequested() {
            Cargo notRouted = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);
            CargoItinerary itinerary = valid();

            // 営業が作業中の予約に経路設計者が手を出すと、引き渡しの記録が
            // 「誰の手番か」を表さなくなる。**組み立てはラムダの外で行う**。中に置くと、
            // フィクスチャ側の例外を期待した例外と取り違える
            assertThatThrownBy(() -> notRouted.assignItinerary(itinerary, LA))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("経路設計を依頼された予約");
        }

        /** 決定 4: 差し替えは ROUTED から許す。予約の状態は動かさない。 */
        @Test
        @DisplayName("経路の差し替えはできる。予約の状態は動かない")
        void allowsReassignment() {
            Cargo assigned = requested().assignItinerary(valid(), LA);
            CargoItinerary replacement = CargoItinerary.of(List.of(
                    Leg.of(VoyageNumber.of("V0201"), TOKYO, BUSAN,
                            Instant.parse("2026-09-02T09:00:00Z"),
                            Instant.parse("2026-09-04T09:00:00Z")),
                    Leg.of(VoyageNumber.of("V0202"), BUSAN, LOS_ANGELES,
                            Instant.parse("2026-09-05T09:00:00Z"),
                            Instant.parse("2026-09-18T09:00:00Z"))));

            // 航海の遅延・欠航は実際に起こる。そのたびに予約を取り直すのは業務が成り立たない
            Cargo replaced = assigned.assignItinerary(replacement, LA);

            assertThat(replaced.itinerary()).contains(replacement);
            assertThat(replaced.bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
            assertThat(replaced.routingStatus()).isEqualTo(RoutingStatus.ROUTED);
        }

        /** 決定 5: 要件を満たさない旅程は断る。 */
        @Test
        @DisplayName("出発地が違う旅程は断る")
        void rejectsItineraryWithWrongOrigin() {
            Cargo cargo = requested();
            CargoItinerary wrongOrigin = itinerary(BUSAN, LOS_ANGELES, "2026-09-15T12:00:00Z");

            // 荷主は貨物を渡せない場所で待つことになる
            assertThatThrownBy(() -> cargo.assignItinerary(wrongOrigin, LA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("予約の条件");
        }

        @Test
        @DisplayName("目的地が違う旅程は断る")
        void rejectsItineraryWithWrongDestination() {
            Cargo cargo = requested();
            CargoItinerary wrongDestination = itinerary(TOKYO, BUSAN, "2026-09-15T12:00:00Z");

            assertThatThrownBy(() -> cargo.assignItinerary(wrongDestination, LA))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("期限を過ぎて着く旅程は断る")
        void rejectsLateItinerary() {
            Cargo cargo = requested();
            CargoItinerary late = itinerary(TOKYO, LOS_ANGELES, "2026-09-25T12:00:00Z");

            // 約束を破ることが確定した状態で予約が進む
            assertThatThrownBy(() -> cargo.assignItinerary(late, LA))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("旅程が無ければ断る")
        void rejectsNullItinerary() {
            Cargo cargo = requested();

            assertThatThrownBy(() -> cargo.assignItinerary(null, LA))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 決定 3: ROUTED も経路設計者に開く。 */
        @Test
        @DisplayName("割り当てた予約も経路設計者が見られる")
        void assignedCargoStaysVisibleToRoutingPlanner() {
            // 割り当てた直後に自分が開けなくなると、確定画面にも旅程にも辿り着けない
            assertThat(requested().assignItinerary(valid(), LA).visibleToRoutingPlanner()).isTrue();
        }

        @Test
        @DisplayName("引き渡されていない予約は、いままでどおり経路設計者に開かない")
        void unrequestedCargoStaysHidden() {
            assertThat(Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE)
                    .visibleToRoutingPlanner()).isFalse();
        }

        /**
         * 予約の訂正（IT6 タスク 0.11）。
         *
         * <p>条件協議の結果が「期限を延ばす」だったとき、<strong>予約を直せないと再依頼しても
         * 同じ結果になる</strong>。営業は予約を作り直すことになり、予約番号が変わって
         * 他サービスの参照が外れる。
         */
        @Test
        @DisplayName("引き渡す前なら、到着期限と出発希望日を直せる")
        void revisesScheduleBeforeHandover() {
            Cargo booked = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);

            Cargo revised = booked.reviseSchedule(
                    LocalDate.of(2026, Month.SEPTEMBER, 5),
                    REVISED_DEADLINE, LA, FIXED_CLOCK);

            assertThat(revised.routeSpecification().departureDate())
                    .contains(LocalDate.of(2026, Month.SEPTEMBER, 5));
            assertThat(revised.routeSpecification().arrivalDeadline())
                    .isEqualTo(REVISED_DEADLINE);
            // 出発地・目的地は変えない。変えるならそれは別の予約である
            assertThat(revised.routeSpecification().origin())
                    .isEqualTo(booked.routeSpecification().origin());
        }

        /** 差し戻された予約こそ直したい。ここを塞ぐと協議の結果を反映できない。 */
        @Test
        @DisplayName("営業へ差し戻された予約も直せる")
        void revisesAfterConsultationRequest() {
            Cargo returned = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE)
                    .requestRouting()
                    .requestConsultation();

            assertThatCode(() -> returned.reviseSchedule(null,
                    REVISED_DEADLINE, LA, FIXED_CLOCK))
                    .doesNotThrowAnyException();
        }

        /**
         * <strong>経路設計者の作業中は直せない。</strong>
         *
         * <p>組んでいる最中に条件が変わると、出来上がった経路が条件を満たさなくなる。
         * 直したいなら、先に協議へ戻す（US10）。
         */
        @Test
        @DisplayName("引き渡し済みの予約は直せない")
        void cannotReviseWhileRoutingPlannerIsWorking() {
            Cargo requested = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE)
                    .requestRouting();

            assertThatThrownBy(() -> requested.reviseSchedule(null,
                    REVISED_DEADLINE, LA, FIXED_CLOCK))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("経路が決まった予約も直せない（先に見直しが要る）")
        void cannotReviseAfterRouteIsAssigned() {
            Cargo assigned = requested().assignItinerary(valid(), LA);

            assertThatThrownBy(() -> assigned.reviseSchedule(null,
                    REVISED_DEADLINE, LA, FIXED_CLOCK))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("到着期限は必須のまま（空にはできない）")
        void keepsArrivalDeadlineRequired() {
            Cargo booked = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);

            assertThatThrownBy(() -> booked.reviseSchedule(null, null, LA, FIXED_CLOCK))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 決定 7: 条件では組めないことを営業へ差し戻す（US10）。 */
        @Test
        @DisplayName("条件では組めないとき、営業へ差し戻せる")
        void requestsConsultation() {
            Cargo returned = requested().requestConsultation();

            assertThat(returned.routingStatus())
                    .isEqualTo(RoutingStatus.CONSULTATION_REQUESTED);
            // 差し戻した本人が確認できなくなると、営業と話したあとに続きができない
            assertThat(returned.visibleToRoutingPlanner()).isTrue();
        }

        @Test
        @DisplayName("引き渡されていない予約は差し戻せない")
        void cannotRequestConsultationBeforeHandover() {
            Cargo notRouted = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);

            assertThatThrownBy(notRouted::requestConsultation)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("経路が決まった予約は差し戻せない（差し替えるべき場面）")
        void cannotRequestConsultationAfterAssignment() {
            Cargo assigned = requested().assignItinerary(valid(), LA);

            assertThatThrownBy(assigned::requestConsultation)
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * ADR-015 のネガティブに書いた「まだ働く場面が無い」検査が、ここで働くようになる。
         *
         * <p><strong>どちらの検査で落ちたかまで確かめる。</strong>例外の型だけを見ると、
         * 後ろの `RoutingStatus` の検査で落ちても緑になり、`BookingStatus` の検査は
         * 依然として無検査のままになる（IT5 レビューの指摘）。
         */
        @Test
        @DisplayName("経路が決まった予約に、経路設計をもう一度依頼することはできない")
        void cannotRequestRoutingAfterAssignment() {
            Cargo assigned = requested().assignItinerary(valid(), LA);

            assertThatThrownBy(assigned::requestRouting)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仮受付の予約だけ");
        }

        /**
         * 決定 7 の裏側。差し戻した予約は、営業がもう一度引き渡せる。
         *
         * <p>ここを塞ぐと、差し戻した予約が誰の手番でもなくなる。
         */
        @Test
        @DisplayName("営業へ差し戻した予約は、条件が決まればもう一度依頼できる")
        void canRequestRoutingAgainAfterConsultation() {
            Cargo returned = requested().requestConsultation();

            Cargo reRequested = returned.requestRouting();

            assertThat(reRequested.routingStatus())
                    .isEqualTo(RoutingStatus.ROUTING_REQUESTED);
        }
    }
}
