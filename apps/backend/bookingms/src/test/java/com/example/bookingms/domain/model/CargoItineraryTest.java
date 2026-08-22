package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 旅程（US09）。
 *
 * <p>Routing Context の {@code TransitPath} と同じ不変条件を、別の型として持つ。あちらは
 * 都度算出して捨てる探索結果、こちらは予約に紐付いて残る記録である。
 */
@DisplayName("旅程")
class CargoItineraryTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location BUSAN = Location.of("KRPUS", "Busan");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private static Leg leg(String voyage, Location from, Location to, String load, String unload) {
        return Leg.of(VoyageNumber.of(voyage), from, to, Instant.parse(load), Instant.parse(unload));
    }

    private static CargoItinerary viaBusan() {
        return CargoItinerary.of(List.of(
                leg("V0100", TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                leg("V0200", BUSAN, LOS_ANGELES, "2026-09-05T08:00:00Z", "2026-09-20T12:00:00Z")));
    }

    @Nested
    @DisplayName("区間のつながり")
    class Connectivity {

        @Test
        @DisplayName("前の区間の荷降し地から次の区間が積み込む")
        void requiresConnectedLegs() {
            List<Leg> disconnected = List.of(
                    leg("V0100", TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                    leg("V0200", SHANGHAI, LOS_ANGELES, "2026-09-05T08:00:00Z",
                            "2026-09-20T12:00:00Z"));

            // 釜山で降ろした貨物が上海から積まれることはない。つながっていない旅程を
            // 保存すると、荷役の担当者は来ない貨物を待つことになる
            assertThatThrownBy(() -> CargoItinerary.of(disconnected))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("つながっていません");
        }

        @Test
        @DisplayName("次の積込は前の荷降しより後でなければならない")
        void requiresChronologicalOrder() {
            List<Leg> goingBack = List.of(
                    leg("V0100", TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                    leg("V0200", BUSAN, LOS_ANGELES, "2026-09-03T17:00:00Z",
                            "2026-09-20T12:00:00Z"));

            assertThatThrownBy(() -> CargoItinerary.of(goingBack))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("荷降し");
        }

        @Test
        @DisplayName("区間が 1 つも無い旅程は作れない")
        void requiresAtLeastOneLeg() {
            assertThatThrownBy(() -> CargoItinerary.of(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CargoItinerary.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("旅程が答えられること")
    class Questions {

        @Test
        @DisplayName("端点は最初の積込地と最後の荷降し地")
        void reportsEndpoints() {
            assertThat(viaBusan().origin()).isEqualTo(TOKYO);
            assertThat(viaBusan().destination()).isEqualTo(LOS_ANGELES);
        }

        @Test
        @DisplayName("到着予定は最後の区間の荷降し時刻")
        void reportsExpectedArrival() {
            assertThat(viaBusan().expectedArrivalTime())
                    .isEqualTo(Instant.parse("2026-09-20T12:00:00Z"));
        }

        @Test
        @DisplayName("出発予定は最初の区間の積込時刻")
        void reportsExpectedDeparture() {
            assertThat(viaBusan().expectedDepartureTime())
                    .isEqualTo(Instant.parse("2026-09-01T09:00:00Z"));
        }

        @Test
        @DisplayName("通る地点かどうかを答えられる")
        void answersWhetherItIncludesLocation() {
            // 誤配の判定（US28）は「そこを通る予定だったか」を旅程に聞く。
            // 荷役の場所と突き合わせるのは呼ぶ側ではなく旅程の仕事にする
            assertThat(viaBusan().includesLocation(TOKYO)).isTrue();
            assertThat(viaBusan().includesLocation(BUSAN)).isTrue();
            assertThat(viaBusan().includesLocation(LOS_ANGELES)).isTrue();
            assertThat(viaBusan().includesLocation(SHANGHAI)).isFalse();
        }

        @Test
        @DisplayName("直行の旅程も作れる")
        void supportsDirectItinerary() {
            CargoItinerary direct = CargoItinerary.of(List.of(
                    leg("V0100", TOKYO, LOS_ANGELES, "2026-09-01T09:00:00Z",
                            "2026-09-20T12:00:00Z")));

            assertThat(direct.legs()).hasSize(1);
            assertThat(direct.origin()).isEqualTo(TOKYO);
            assertThat(direct.destination()).isEqualTo(LOS_ANGELES);
        }
    }

    @Nested
    @DisplayName("復元")
    class Restoration {

        @Test
        @DisplayName("永続化された行は検査せずそのまま読む")
        void restoreDoesNotValidate() {
            // 連結の規則が無かったころの行が読めなくなると、一覧そのものが開けなくなる
            CargoItinerary restored = CargoItinerary.restore(List.of(
                    leg("V0100", TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                    leg("V0200", SHANGHAI, LOS_ANGELES, "2026-09-05T08:00:00Z",
                            "2026-09-20T12:00:00Z")));

            assertThat(restored.legs()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("区間そのもの")
    class LegItself {

        @Test
        @DisplayName("積込地と荷降し地は同じにできない")
        void rejectsSameEndpoints() {
            assertThatThrownBy(() -> leg("V0100", TOKYO, TOKYO,
                    "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("荷降しは積込より後でなければならない")
        void rejectsUnloadBeforeLoad() {
            assertThatThrownBy(() -> leg("V0100", TOKYO, BUSAN,
                    "2026-09-03T18:00:00Z", "2026-09-01T09:00:00Z"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("どの航海で運ぶかは必須")
        void requiresVoyageNumber() {
            Instant load = Instant.parse("2026-09-01T09:00:00Z");
            Instant unload = Instant.parse("2026-09-03T18:00:00Z");

            assertThatThrownBy(() -> Leg.of(null, TOKYO, BUSAN, load, unload))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("航海番号は空にできない")
        void rejectsBlankVoyageNumber() {
            assertThatThrownBy(() -> VoyageNumber.of(" "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("同じ内容の区間は同じ値として扱う")
        void equality() {
            assertThat(leg("V0100", TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"))
                    .isEqualTo(leg("V0100", TOKYO, BUSAN, "2026-09-01T09:00:00Z",
                            "2026-09-03T18:00:00Z"));
        }
    }
}
