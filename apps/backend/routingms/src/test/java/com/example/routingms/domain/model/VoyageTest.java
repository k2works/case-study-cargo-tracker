package com.example.routingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("航海スケジュール")
class VoyageTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location BUSAN = Location.of("KRPUS", "Busan");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private static Instant at(String isoInstant) {
        return Instant.parse(isoInstant);
    }

    private static CarrierMovement leg(Location from, Location to, String departure, String arrival) {
        return CarrierMovement.of(from, to, at(departure), at(arrival));
    }

    private static Voyage voyage(List<CarrierMovement> movements, Set<CargoType> supported) {
        return Voyage.register(VoyageNumber.of("V0100"), "さくら丸", "日本郵船",
                supported, Schedule.of(movements));
    }

    private static List<CarrierMovement> tokyoToLosAngelesViaBusan() {
        return List.of(
                leg(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                leg(BUSAN, LOS_ANGELES, "2026-09-04T08:00:00Z", "2026-09-18T12:00:00Z"));
    }

    @Nested
    @DisplayName("登録するとき")
    class WhenRegistered {

        @Test
        @DisplayName("航海番号・船名・運送会社・寄港地を保持する")
        void holdsItsIdentity() {
            Voyage voyage = voyage(tokyoToLosAngelesViaBusan(), Set.of(CargoType.GENERAL));

            assertThat(voyage.voyageNumber()).isEqualTo(VoyageNumber.of("V0100"));
            assertThat(voyage.vesselName()).isEqualTo("さくら丸");
            assertThat(voyage.carrierName()).isEqualTo("日本郵船");
            assertThat(voyage.schedule().origin()).isEqualTo(TOKYO);
            assertThat(voyage.schedule().destination()).isEqualTo(LOS_ANGELES);
        }

        /**
         * 船名と運送会社は必須にする。
         *
         * <p>どの船かが分からないと、荷役の現場と問い合わせ窓口が貨物を追えない。
         * 「後で入れる」を許すと、入っていない航海が業務に混ざる。
         */
        @Test
        @DisplayName("船名・運送会社の無い航海は登録できない")
        void rejectsMissingVesselOrCarrier() {
            List<CarrierMovement> movements = tokyoToLosAngelesViaBusan();

            assertThatThrownBy(() -> Voyage.register(VoyageNumber.of("V0100"), " ", "日本郵船",
                            Set.of(CargoType.GENERAL), Schedule.of(movements)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("船名");
            assertThatThrownBy(() -> Voyage.register(VoyageNumber.of("V0100"), "さくら丸", " ",
                            Set.of(CargoType.GENERAL), Schedule.of(movements)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("運送会社");
        }

        /**
         * 何も運べない航海を作らせない。
         *
         * <p>登録は通るが検索に一切出てこない、という形で表れる。原因が分からないまま
         * 「経路が見つからない」だけが残る。
         */
        @Test
        @DisplayName("対応できる貨物種別が空の航海は登録できない")
        void rejectsEmptySupportedCargoTypes() {
            List<CarrierMovement> movements = tokyoToLosAngelesViaBusan();

            assertThatThrownBy(() -> Voyage.register(VoyageNumber.of("V0100"), "さくら丸", "日本郵船",
                            Set.of(), Schedule.of(movements)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("貨物種別");
        }

        @Test
        @DisplayName("寄港地の無い航海は登録できない")
        void rejectsEmptySchedule() {
            assertThatThrownBy(() -> Schedule.of(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("区間");
        }
    }

    @Nested
    @DisplayName("運送区間")
    class Movements {

        @Test
        @DisplayName("出発日が到着日より後の区間は受け付けない")
        void rejectsArrivalBeforeDeparture() {
            assertThatThrownBy(() -> leg(TOKYO, BUSAN, "2026-09-03T18:00:00Z", "2026-09-01T09:00:00Z"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("到着");
        }

        @Test
        @DisplayName("出発地と到着地が同じ区間は受け付けない")
        void rejectsSameEndpoints() {
            assertThatThrownBy(() -> leg(TOKYO, TOKYO, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("同じ");
        }

        /**
         * 前の区間の到着地が次の区間の出発地であること。
         *
         * <p>つながっていない区間の並びは「航海」ではない。ここを通すと、経路候補算出（IT4）が
         * 実在しない乗り継ぎを提案する。
         */
        @Test
        @DisplayName("前の区間の到着地から次の区間が出ていない並びは受け付けない")
        void rejectsDisconnectedMovements() {
            List<CarrierMovement> disconnected = List.of(
                    leg(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                    leg(LOS_ANGELES, TOKYO, "2026-09-04T08:00:00Z", "2026-09-18T12:00:00Z"));

            assertThatThrownBy(() -> Schedule.of(disconnected))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("つながって");
        }

        @Test
        @DisplayName("次の区間が前の区間の到着より前に出発する並びは受け付けない")
        void rejectsOverlappingMovements() {
            List<CarrierMovement> goingBackInTime = List.of(
                    leg(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                    leg(BUSAN, LOS_ANGELES, "2026-09-02T08:00:00Z", "2026-09-18T12:00:00Z"));

            assertThatThrownBy(() -> Schedule.of(goingBackInTime))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("到着より前");
        }

        @Test
        @DisplayName("寄港の順序が保たれる")
        void keepsCallingOrder() {
            Voyage voyage = voyage(tokyoToLosAngelesViaBusan(), Set.of(CargoType.GENERAL));

            assertThat(voyage.schedule().callingPorts()).containsExactly(TOKYO, BUSAN, LOS_ANGELES);
            assertThat(voyage.departureTime(TOKYO)).contains(at("2026-09-01T09:00:00Z"));
            assertThat(voyage.arrivalTime(LOS_ANGELES)).contains(at("2026-09-18T12:00:00Z"));
            assertThat(voyage.departureTime(LOS_ANGELES)).isEmpty();
        }
    }

    @Nested
    @DisplayName("貨物と経路の適合")
    class Suitability {

        @Test
        @DisplayName("対応していない貨物種別は運べない")
        void supportsOnlyDeclaredCargoTypes() {
            Voyage general = voyage(tokyoToLosAngelesViaBusan(), Set.of(CargoType.GENERAL));
            Voyage hazardous = voyage(tokyoToLosAngelesViaBusan(),
                    Set.of(CargoType.GENERAL, CargoType.HAZARDOUS));

            assertThat(general.supports(CargoType.GENERAL)).isTrue();
            assertThat(general.supports(CargoType.HAZARDOUS)).isFalse();
            assertThat(general.supports(CargoType.REFRIGERATED)).isFalse();
            assertThat(hazardous.supports(CargoType.HAZARDOUS)).isTrue();
        }

        @Test
        @DisplayName("寄港の順序どおりの区間だけをつなぐ（積み替えを含む）")
        void connectsPortsInCallingOrder() {
            Voyage voyage = voyage(tokyoToLosAngelesViaBusan(), Set.of(CargoType.GENERAL));

            assertThat(voyage.connects(TOKYO, LOS_ANGELES)).isTrue();
            assertThat(voyage.connects(TOKYO, BUSAN)).isTrue();
            assertThat(voyage.connects(BUSAN, LOS_ANGELES)).isTrue();
            // 逆向きには運べない。同じ港の集合を持つことと、運べることは別である
            assertThat(voyage.connects(LOS_ANGELES, TOKYO)).isFalse();
            assertThat(voyage.connects(TOKYO, TOKYO)).isFalse();
        }

        @Test
        @DisplayName("寄港しない港は起点にも終点にもならない")
        void doesNotConnectPortsItDoesNotCall() {
            Voyage voyage = Voyage.register(VoyageNumber.of("V0200"), "つばき丸", "商船三井",
                    Set.of(CargoType.GENERAL),
                    Schedule.of(List.of(
                            leg(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"))));

            assertThat(voyage.connects(TOKYO, LOS_ANGELES)).isFalse();
            assertThat(voyage.connects(LOS_ANGELES, BUSAN)).isFalse();
        }
    }

    @Nested
    @DisplayName("航海番号")
    class Number {

        @Test
        @DisplayName("空の航海番号は受け付けない")
        void rejectsBlank() {
            assertThatThrownBy(() -> VoyageNumber.of(" "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("航海番号");
        }

        @Test
        @DisplayName("前後の空白は落として同一視する")
        void trimsAndCompares() {
            assertThat(VoyageNumber.of(" V0100 ")).isEqualTo(VoyageNumber.of("V0100"));
        }
    }
}
