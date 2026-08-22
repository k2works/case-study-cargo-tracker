package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("貨物予約")
class CargoTest {

    private static final RouteSpecification ROUTE = RouteSpecification.restore(
            Location.of("JPTYO", "Tokyo"), Location.of("USLAX", "Los Angeles"),
            LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 20));

    private static final HazardousDeclaration DECLARATION =
            HazardousDeclaration.of("3", "UN1263", "PAINT");

    private static final TemperatureRequirement TEMPERATURE =
            TemperatureRequirement.of(new BigDecimal("-20"), new BigDecimal("-15"));

    private static CargoSpecification specification(CargoType type,
            HazardousDeclaration declaration, TemperatureRequirement temperature) {
        return new CargoSpecification(type, new BigDecimal("12000"), 20, "電子部品",
                Dimensions.of(new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100")),
                declaration, temperature);
    }

    @Nested
    @DisplayName("受け付けたとき")
    class WhenBooked {

        @Test
        @DisplayName("仮受付になり、まだ動いていない状態を持つ")
        void startsAsPreliminary() {
            Cargo cargo = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);

            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.PRELIMINARY);
            // 「まだ動いていない」は空欄ではなく意味のある状態（ADR-009）
            assertThat(cargo.transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
            assertThat(cargo.routingStatus()).isEqualTo(RoutingStatus.NOT_ROUTED);
        }

        @Test
        @DisplayName("予約番号はまだ持たない（採番は永続化の経路で行う）")
        void hasNoBookingIdYet() {
            // 集約側で組み立てるとシーケンスと衝突した番号を発行できてしまう（ADR-011）
            Cargo cargo = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);

            assertThat(cargo.bookingId()).isEmpty();
        }

        @Test
        @DisplayName("貨物仕様と輸送条件を保持する")
        void holdsSpecifications() {
            Cargo cargo = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);

            assertThat(cargo.shipperId()).isEqualTo(1L);
            assertThat(cargo.type()).isEqualTo(CargoType.GENERAL);
            assertThat(cargo.weightKg()).isEqualByComparingTo(new BigDecimal("12000"));
            assertThat(cargo.routeSpecification()).isEqualTo(ROUTE);
            assertThat(cargo.specification().quantity()).isEqualTo(20);
            assertThat(cargo.specification().description()).isEqualTo("電子部品");
        }

        @Test
        @DisplayName("荷主・輸送条件・種別が無い予約は受け付けない")
        void rejectsMissingEssentials() {
            // 仕様の組み立てはラムダの外で済ませる。中に置くと、そちらが投げただけでも
            // テストが通り、確かめたい検査が働いていなくても気づけない
            CargoSpecification general = specification(CargoType.GENERAL, null, null);
            CargoSpecification noType = specification(null, null, null);

            assertThatThrownBy(() -> Cargo.book(null, general, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("荷主");
            assertThatThrownBy(() -> Cargo.book(1L, general, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("輸送条件");
            assertThatThrownBy(() -> Cargo.book(1L, noType, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("貨物種別");
            assertThatThrownBy(() -> Cargo.book(1L, null, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("重量が 0 以下の予約は受け付けない")
        void rejectsNonPositiveWeight() {
            CargoSpecification zero = CargoSpecification.general(BigDecimal.ZERO, null, null, null);

            assertThatThrownBy(() -> Cargo.book(1L, zero, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("重量");
        }

        @Test
        @DisplayName("個数が 0 以下の予約は受け付けない")
        void rejectsNonPositiveQuantity() {
            CargoSpecification zero =
                    CargoSpecification.general(new BigDecimal("100"), 0, null, null);

            assertThatThrownBy(() -> Cargo.book(1L, zero, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("個数");
        }

        @Test
        @DisplayName("個数・品名・寸法は任意")
        void allowsOptionalSpecification() {
            CargoSpecification minimal =
                    CargoSpecification.general(new BigDecimal("100"), null, null, null);

            assertThatCode(() -> Cargo.book(1L, minimal, ROUTE)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("危険物・冷凍の追加情報（US05）")
    class SpecialCargo {

        @Test
        @DisplayName("危険物は申告が必須")
        void hazardousRequiresDeclaration() {
            CargoSpecification withoutDeclaration = specification(CargoType.HAZARDOUS, null, null);

            assertThatThrownBy(() -> Cargo.book(1L, withoutDeclaration, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("危険物申告");
        }

        @Test
        @DisplayName("冷凍・冷蔵は温度条件が必須")
        void refrigeratedRequiresTemperature() {
            CargoSpecification withoutTemperature =
                    specification(CargoType.REFRIGERATED, null, null);

            assertThatThrownBy(() -> Cargo.book(1L, withoutTemperature, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("保管温度");
        }

        @Test
        @DisplayName("一般貨物に危険物申告や温度条件は付けられない")
        void generalCannotCarrySpecialInformation() {
            // 付け忘れと同じく、付けすぎも誤り。経路設計（IT3）が扱いを判断できなくなる
            CargoSpecification withDeclaration = specification(CargoType.GENERAL, DECLARATION, null);
            CargoSpecification withTemperature = specification(CargoType.GENERAL, null, TEMPERATURE);

            assertThatThrownBy(() -> Cargo.book(1L, withDeclaration, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("危険物にだけ");
            assertThatThrownBy(() -> Cargo.book(1L, withTemperature, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("冷凍・冷蔵貨物にだけ");
        }

        /**
         * どちらの検査で落ちたかまで確かめる。
         *
         * <p>例外の型だけを見ると、片方の検査を消しても、もう片方が落とすため緑のままになる。
         * 「例外が出た」ことではなく「その検査が働いた」ことを判別する。
         */
        @Test
        @DisplayName("危険物に温度条件、冷凍に危険物申告は付けられない")
        void cannotMixSpecialInformation() {
            CargoSpecification hazardousWithTemperature =
                    specification(CargoType.HAZARDOUS, DECLARATION, TEMPERATURE);
            CargoSpecification refrigeratedWithDeclaration =
                    specification(CargoType.REFRIGERATED, DECLARATION, TEMPERATURE);

            assertThatThrownBy(() -> Cargo.book(1L, hazardousWithTemperature, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("保管温度の条件は冷凍・冷蔵貨物にだけ");
            assertThatThrownBy(() -> Cargo.book(1L, refrigeratedWithDeclaration, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("危険物申告は危険物にだけ");
        }

        @Test
        @DisplayName("正しく揃っていれば受け付け、経路設計が読める形で保持する")
        void acceptsWellFormedSpecialCargo() {
            Cargo hazardous =
                    Cargo.book(1L, specification(CargoType.HAZARDOUS, DECLARATION, null), ROUTE);
            Cargo refrigerated =
                    Cargo.book(1L, specification(CargoType.REFRIGERATED, null, TEMPERATURE), ROUTE);

            assertThat(hazardous.requiresHazardousDeclaration()).isTrue();
            assertThat(hazardous.hazardousDeclaration()).contains(DECLARATION);
            assertThat(hazardous.requiresTemperatureRequirement()).isFalse();
            assertThat(hazardous.temperatureRequirement()).isEmpty();

            assertThat(refrigerated.requiresTemperatureRequirement()).isTrue();
            assertThat(refrigerated.temperatureRequirement()).contains(TEMPERATURE);
            assertThat(refrigerated.requiresHazardousDeclaration()).isFalse();
            assertThat(refrigerated.hazardousDeclaration()).isEmpty();
        }
    }

    @Test
    @DisplayName("復元では検査しない（規則が無かったころの行が読めなくなる）")
    void restoreDoesNotValidate() {
        Cargo restored = Cargo.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                CargoStatus.preliminary(),
                // 危険物なのに申告が無い（列が無かったころの行）
                specification(CargoType.HAZARDOUS, null, null), ROUTE);

        assertThat(restored.bookingId()).contains(BookingId.of("BKG-2026000001"));
        assertThat(restored.hazardousDeclaration()).isEmpty();
        assertThat(restored.id()).isEqualTo(1L);
    }

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
