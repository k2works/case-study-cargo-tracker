package com.example.routingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("経路候補")
class TransitPathTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location BUSAN = Location.of("KRPUS", "Busan");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private static Instant at(String isoInstant) {
        return Instant.parse(isoInstant);
    }

    private static TransitEdge edge(Location from, Location to, String departure, String arrival) {
        return TransitEdge.of(VoyageNumber.of("V0100"), from, to, at(departure), at(arrival));
    }

    @Nested
    @DisplayName("区間のつながり")
    class Connectivity {

        @Test
        @DisplayName("前の区間の到着地から次の区間が出発する")
        void requiresConnectedEdges() {
            List<TransitEdge> disconnected = List.of(
                    edge(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                    edge(SHANGHAI, LOS_ANGELES, "2026-09-05T08:00:00Z", "2026-09-20T12:00:00Z"));

            assertThatThrownBy(() -> TransitPath.of(disconnected))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("つながっていません");
        }

        @Test
        @DisplayName("区間が 1 つも無い経路は経路ではない")
        void requiresAtLeastOneEdge() {
            assertThatThrownBy(() -> TransitPath.of(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * 同じ港での積み替えには時間が要る。
         *
         * <p>降ろして、運んで、積む。ここを 0 にすると、机上では成立するが現場で実行できない
         * 経路を候補に出す。経路設計者はそれを見抜けず、動かない予定が下流へ流れる。
         */
        @Test
        @DisplayName("積み替えに要する最低時間を満たさない乗り継ぎは経路にならない")
        void requiresMinimumTransshipmentTime() {
            List<TransitEdge> tooTight = List.of(
                    edge(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                    edge(BUSAN, LOS_ANGELES, "2026-09-03T23:59:00Z", "2026-09-18T12:00:00Z"));

            assertThatThrownBy(() -> TransitPath.of(tooTight))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("積み替え");
        }

        /** 境界。ちょうど最低時間なら成立する。 */
        @Test
        @DisplayName("ちょうど最低時間の乗り継ぎは成立する")
        void acceptsExactlyMinimumTransshipmentTime() {
            List<TransitEdge> exact = List.of(
                    edge(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                    edge(BUSAN, LOS_ANGELES, "2026-09-04T00:00:00Z", "2026-09-18T12:00:00Z"));

            assertThat(TransitPath.of(exact).edges()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("経路から読み取れること")
    class DerivedFacts {

        private TransitPath viaBusan() {
            return TransitPath.of(List.of(
                    edge(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                    edge(BUSAN, LOS_ANGELES, "2026-09-04T08:00:00Z", "2026-09-18T12:00:00Z")));
        }

        @Test
        @DisplayName("出発地・目的地・出発日時・到着日時は端の区間から決まる")
        void readsEndpoints() {
            TransitPath path = viaBusan();

            assertThat(path.origin()).isEqualTo(TOKYO);
            assertThat(path.destination()).isEqualTo(LOS_ANGELES);
            assertThat(path.departureTime()).isEqualTo(at("2026-09-01T09:00:00Z"));
            assertThat(path.arrivalTime()).isEqualTo(at("2026-09-18T12:00:00Z"));
        }

        /**
         * 輸送日数は「荷主が待つ日数」であり、区間の移動時間の合計ではない。
         *
         * <p>積み替えの待ち時間も荷主にとっては待ち時間である。区間だけを足すと、
         * 乗り継ぎの多い経路が実際より短く見え、推奨順が狂う。
         */
        @Test
        @DisplayName("輸送日数は出発から到着までで、積み替えの待ち時間を含む")
        void countsTransitDaysIncludingWaiting() {
            assertThat(viaBusan().transitDays()).isEqualTo(17);
        }

        @Test
        @DisplayName("経由港は途中で乗り継ぐ港だけで、出発地と目的地を含まない")
        void listsTransitPortsOnly() {
            assertThat(viaBusan().transitPorts()).containsExactly(BUSAN);
        }

        @Test
        @DisplayName("直行便は積み替えが 0 回で、経由港を持たない")
        void directVoyageHasNoTransshipment() {
            TransitPath direct = TransitPath.of(List.of(
                    edge(TOKYO, LOS_ANGELES, "2026-09-01T09:00:00Z", "2026-09-15T12:00:00Z")));

            assertThat(direct.transshipmentCount()).isZero();
            assertThat(direct.transitPorts()).isEmpty();
            assertThat(direct.isDirect()).isTrue();
        }

        @Test
        @DisplayName("積み替え回数は区間の数より 1 少ない")
        void countsTransshipments() {
            assertThat(viaBusan().transshipmentCount()).isEqualTo(1);
            assertThat(viaBusan().isDirect()).isFalse();
        }

        /**
         * 経路そのものを 1 つの表現として比べる。
         *
         * <p>項目ごとの比較を積み上げると、属性が増えるたび同じ穴が空く（IT3 US25 の
         * 差分算出がそうだった）。値オブジェクトとして丸ごと等価性を持たせる。
         */
        @Test
        @DisplayName("経路は丸ごと 1 つの値として比べられる")
        void comparesAsAWhole() {
            assertThat(viaBusan()).isEqualTo(viaBusan());

            TransitPath laterArrival = TransitPath.of(List.of(
                    edge(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"),
                    edge(BUSAN, LOS_ANGELES, "2026-09-04T08:00:00Z", "2026-09-20T12:00:00Z")));

            // 到着だけが違う経路は、別の経路である
            assertThat(viaBusan()).isNotEqualTo(laterArrival);
        }
    }

    @Nested
    @DisplayName("区間")
    class Edges {

        @Test
        @DisplayName("同じ港へは移動しない")
        void rejectsSameEndpoints() {
            assertThatThrownBy(() -> edge(TOKYO, TOKYO, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("到着は出発より後である")
        void rejectsArrivalBeforeDeparture() {
            assertThatThrownBy(() -> edge(TOKYO, BUSAN, "2026-09-03T18:00:00Z", "2026-09-01T09:00:00Z"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("どの航海で運ぶかを持つ")
        void carriesTheVoyageNumber() {
            TransitEdge leg = edge(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z");

            assertThat(leg.voyageNumber()).isEqualTo(VoyageNumber.of("V0100"));
        }
    }
}
