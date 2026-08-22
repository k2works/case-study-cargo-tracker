package com.example.routingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 経路候補算出（US08）。
 *
 * <p>探索そのものを、画面もサービス層も無いところで固める。制約の判断がサービス層へ
 * 漏れると、画面と経路探索が別々の答えを出すようになる。
 */
@DisplayName("経路候補算出")
class TransitPathFinderTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location BUSAN = Location.of("KRPUS", "Busan");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");
    private static final Location SINGAPORE = Location.of("SGSIN", "Singapore");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private final TransitPathFinder finder = new TransitPathFinder();

    private static Instant at(String isoInstant) {
        return Instant.parse(isoInstant);
    }

    private static CarrierMovement leg(Location from, Location to, String departure, String arrival) {
        return CarrierMovement.of(from, to, at(departure), at(arrival));
    }

    private static Voyage voyage(String number, Set<CargoType> supported, CarrierMovement... legs) {
        return Voyage.register(VoyageNumber.of(number), "船 " + number, "運送会社",
                supported, Schedule.of(List.of(legs)));
    }

    private static Voyage general(String number, CarrierMovement... legs) {
        return voyage(number, Set.of(CargoType.GENERAL), legs);
    }

    private static RouteSearchSpecification toLosAngelesBy(String deadline) {
        return RouteSearchSpecification.of(TOKYO, LOS_ANGELES, at(deadline), CargoType.GENERAL);
    }

    /** 直行便。 */
    private static Voyage direct() {
        return general("V-DIRECT", leg(TOKYO, LOS_ANGELES, "2026-09-01T09:00:00Z", "2026-09-15T12:00:00Z"));
    }

    /** 釜山で 1 回積み替える 2 本。 */
    private static Voyage toBusan() {
        return general("V-BUSAN-1", leg(TOKYO, BUSAN, "2026-09-01T10:00:00Z", "2026-09-03T18:00:00Z"));
    }

    private static Voyage fromBusan() {
        return general("V-BUSAN-2", leg(BUSAN, LOS_ANGELES, "2026-09-04T08:00:00Z", "2026-09-19T12:00:00Z"));
    }

    @Nested
    @DisplayName("候補の集合")
    class Candidates {

        @Test
        @DisplayName("直行便が候補になる")
        void findsDirectVoyage() {
            List<TransitPath> paths =
                    finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"), List.of(direct()));

            assertThat(paths).hasSize(1);
            assertThat(paths.get(0).isDirect()).isTrue();
            assertThat(paths.get(0).voyageNumbers()).containsExactly(VoyageNumber.of("V-DIRECT"));
        }

        @Test
        @DisplayName("1 回の積み替えでつながる経路が候補になる")
        void findsOneTransshipment() {
            List<TransitPath> paths = finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"),
                    List.of(toBusan(), fromBusan()));

            assertThat(paths).hasSize(1);
            TransitPath path = paths.get(0);
            assertThat(path.transitPorts()).containsExactly(BUSAN);
            assertThat(path.voyageNumbers())
                    .containsExactly(VoyageNumber.of("V-BUSAN-1"), VoyageNumber.of("V-BUSAN-2"));
        }

        @Test
        @DisplayName("2 回の積み替えでつながる経路も候補になる")
        void findsTwoTransshipments() {
            List<TransitPath> paths = finder.find(toLosAngelesBy("2026-10-30T00:00:00Z"), List.of(
                    general("V-A", leg(TOKYO, SHANGHAI, "2026-09-01T09:00:00Z", "2026-09-03T09:00:00Z")),
                    general("V-B", leg(SHANGHAI, SINGAPORE, "2026-09-04T09:00:00Z", "2026-09-08T09:00:00Z")),
                    general("V-C", leg(SINGAPORE, LOS_ANGELES, "2026-09-09T09:00:00Z", "2026-09-28T09:00:00Z"))));

            assertThat(paths).hasSize(1);
            assertThat(paths.get(0).transitPorts()).containsExactly(SHANGHAI, SINGAPORE);
        }

        @Test
        @DisplayName("直行便と積み替え便が両方あれば、両方が候補になる")
        void findsAllReachableRoutes() {
            List<TransitPath> paths = finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"),
                    List.of(direct(), toBusan(), fromBusan()));

            assertThat(paths).hasSize(2);
        }

        /** 1 本の航海が出発地と目的地の両方に寄るなら、それだけで運べる。 */
        @Test
        @DisplayName("途中に寄港する航海は、その区間だけを使って候補になる")
        void usesASegmentOfALongerVoyage() {
            Voyage viaTokyo = general("V-LONG",
                    leg(SHANGHAI, TOKYO, "2026-08-28T09:00:00Z", "2026-08-31T09:00:00Z"),
                    leg(TOKYO, LOS_ANGELES, "2026-09-01T09:00:00Z", "2026-09-16T09:00:00Z"));

            List<TransitPath> paths =
                    finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"), List.of(viaTokyo));

            assertThat(paths).hasSize(1);
            assertThat(paths.get(0).origin()).isEqualTo(TOKYO);
            assertThat(paths.get(0).departureTime()).isEqualTo(at("2026-09-01T09:00:00Z"));
        }
    }

    @Nested
    @DisplayName("到着期限")
    class Deadline {

        /**
         * 期限は「その時刻までに着けばよい」という約束である。
         *
         * <p>ちょうど着いた貨物は約束を守っている。ここを「より前」にすると、期限ちょうどの
         * 便だけが黙って候補から消える。荷主が期限に合わせて予約した便ほど消えるため、
         * 症状は「いちばん使いたい便が出てこない」形で現れる。
         *
         * <p>境界そのもののデータで検査する。「十分に早い / 十分に遅い」だけのデータでは、
         * 包含の向きを反転させても緑のままになる。
         */
        @Test
        @DisplayName("期限ちょうどに着く経路は候補に出る")
        void includesArrivalExactlyAtTheDeadline() {
            List<TransitPath> paths =
                    finder.find(toLosAngelesBy("2026-09-15T12:00:00Z"), List.of(direct()));

            assertThat(paths).hasSize(1);
            assertThat(paths.get(0).arrivalTime()).isEqualTo(at("2026-09-15T12:00:00Z"));
        }

        @Test
        @DisplayName("期限を 1 分でも過ぎる経路は候補に出ない")
        void excludesArrivalOneMinuteLate() {
            List<TransitPath> paths =
                    finder.find(toLosAngelesBy("2026-09-15T11:59:00Z"), List.of(direct()));

            assertThat(paths).isEmpty();
        }

        /** 積み替えのある経路でも、期限の包含は同じ向きである。 */
        @Test
        @DisplayName("積み替えのある経路も、期限ちょうどなら候補に出る")
        void includesTransshipmentArrivingExactlyAtTheDeadline() {
            List<TransitPath> paths = finder.find(toLosAngelesBy("2026-09-19T12:00:00Z"),
                    List.of(toBusan(), fromBusan()));

            assertThat(paths).hasSize(1);
        }

        @Test
        @DisplayName("積み替えのある経路も、期限を 1 分過ぎれば候補に出ない")
        void excludesTransshipmentOneMinuteLate() {
            List<TransitPath> paths = finder.find(toLosAngelesBy("2026-09-19T11:59:00Z"),
                    List.of(toBusan(), fromBusan()));

            assertThat(paths).isEmpty();
        }
    }

    @Nested
    @DisplayName("積み替えに要する時間")
    class TransshipmentTime {

        /**
         * 探索側でも 6 時間を守る。
         *
         * <p>{@link TransitPath#of} にも同じ検査があるが、<strong>探索が先に弾くため
         * その経路には到達しない</strong>。つまり探索側のガードを外すと、二重に見えて
         * 実は無防備になる。ここを外すと、降ろして数十分後に別の船へ積む、現場で実行
         * できない経路が候補に出る。しかも乗り継ぎが短いほど早く着くため<strong>推奨順で
         * 上に来る</strong>。
         */
        @Test
        @DisplayName("積み替えが 6 時間に満たない乗り継ぎは候補にしない")
        void excludesTooTightTransshipment() {
            Voyage first = general("V-IN",
                    leg(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"));
            Voyage tooSoon = general("V-OUT-TOO-SOON",
                    leg(BUSAN, LOS_ANGELES, "2026-09-03T23:59:00Z", "2026-09-18T12:00:00Z"));

            assertThat(finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"),
                    List.of(first, tooSoon))).isEmpty();
        }

        /** 境界。ちょうど 6 時間空いていれば候補になる。 */
        @Test
        @DisplayName("積み替えがちょうど 6 時間なら候補になる")
        void includesExactlySixHourTransshipment() {
            Voyage first = general("V-IN",
                    leg(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T18:00:00Z"));
            Voyage justEnough = general("V-OUT-JUST-ENOUGH",
                    leg(BUSAN, LOS_ANGELES, "2026-09-04T00:00:00Z", "2026-09-18T12:00:00Z"));

            assertThat(finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"),
                    List.of(first, justEnough))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("同じ航海を積み替えにしない")
    class SameVoyage {

        /**
         * 1 本の航海で通しで運べるなら、それは積み替えではない。
         *
         * <p>途中の寄港地で「降りてまた同じ船に乗る」経路を作ると、出発も到着も船も同じ 2 行が
         * 並ぶ。片方には「直行」が付き、もう片方は区間と港のぶん高い費用で「積み替え 1 回」と
         * 出る。<strong>存在しない選択肢と存在しない価格差</strong>を経路設計者に見せることになり、
         * 一覧の件数も推奨順の意味も崩れる。
         */
        @Test
        @DisplayName("途中に寄港する 1 本の航海は、直行の 1 件だけを候補にする")
        void doesNotSplitASingleVoyageIntoTransshipments() {
            Voyage viaSingapore = general("V-THROUGH",
                    leg(TOKYO, SINGAPORE, "2026-09-01T09:00:00Z", "2026-09-05T09:00:00Z"),
                    leg(SINGAPORE, LOS_ANGELES, "2026-09-06T09:00:00Z", "2026-09-20T09:00:00Z"));

            List<TransitPath> paths =
                    finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"), List.of(viaSingapore));

            assertThat(paths).hasSize(1);
            assertThat(paths.get(0).isDirect()).isTrue();
            assertThat(paths.get(0).transitPorts()).isEmpty();
        }

        /** 別の航海への乗り継ぎは、これまでどおり積み替えとして候補になる。 */
        @Test
        @DisplayName("別の航海への乗り継ぎは積み替えとして候補になる")
        void stillFindsTransshipmentsBetweenDifferentVoyages() {
            List<TransitPath> paths = finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"),
                    List.of(toBusan(), fromBusan()));

            assertThat(paths).hasSize(1);
            assertThat(paths.get(0).transshipmentCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("候補にならないもの")
    class Excluded {

        @Test
        @DisplayName("対応していない貨物種別の航海は使わない")
        void excludesUnsupportedCargoType() {
            Voyage generalOnly = direct();

            List<TransitPath> paths = finder.find(
                    RouteSearchSpecification.of(TOKYO, LOS_ANGELES,
                            at("2026-09-30T00:00:00Z"), CargoType.HAZARDOUS),
                    List.of(generalOnly));

            assertThat(paths).isEmpty();
        }

        @Test
        @DisplayName("前の便の到着より前に出る便には乗り継げない")
        void excludesImpossibleConnection() {
            Voyage tooEarly = general("V-EARLY",
                    leg(BUSAN, LOS_ANGELES, "2026-09-02T08:00:00Z", "2026-09-17T12:00:00Z"));

            List<TransitPath> paths =
                    finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"), List.of(toBusan(), tooEarly));

            assertThat(paths).isEmpty();
        }

        @Test
        @DisplayName("目的地へ到達しない航海だけでは候補が出ない")
        void excludesDeadEnds() {
            List<TransitPath> paths =
                    finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"), List.of(toBusan()));

            assertThat(paths).isEmpty();
        }

        /**
         * 積み替えの上限。
         *
         * <p>上限が無いと、同じ港を経由し続ける経路を延々と作り、探索が終わらない。
         * 業務としても、3 回以上の積み替えは貨物の損傷と遅延の危険が上がるため候補にしない。
         */
        @Test
        @DisplayName("積み替えの上限を超える経路は候補にしない")
        void excludesTooManyTransshipments() {
            List<Voyage> chain = List.of(
                    general("V-1", leg(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-02T09:00:00Z")),
                    general("V-2", leg(BUSAN, SHANGHAI, "2026-09-03T09:00:00Z", "2026-09-04T09:00:00Z")),
                    general("V-3", leg(SHANGHAI, SINGAPORE, "2026-09-05T09:00:00Z", "2026-09-06T09:00:00Z")),
                    general("V-4", leg(SINGAPORE, LOS_ANGELES, "2026-09-07T09:00:00Z", "2026-09-20T09:00:00Z")));

            assertThat(finder.find(toLosAngelesBy("2026-10-30T00:00:00Z"), chain)).isEmpty();
        }

        /**
         * 一度出た港へ戻る経路は候補にしない。
         *
         * <p>往復航海があると、素朴な探索は「東京 → 釜山 → 東京 → ロサンゼルス」を
         * 見つけてしまう。港の往復のぶんだけ遅く、荷役の回数も増えるだけで、
         * <strong>業務としては意味が無い</strong>。積み替えの上限を緩めた再算出で
         * 真っ先に現れるため、上限だけでは防げない。
         */
        @Test
        @DisplayName("出た港へ戻ってから向かう経路は候補にしない")
        void excludesRoutesReturningToAVisitedPort() {
            Voyage roundTrip = general("V-ROUND",
                    leg(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-02T09:00:00Z"),
                    leg(BUSAN, TOKYO, "2026-09-03T09:00:00Z", "2026-09-04T09:00:00Z"));
            Voyage later = general("V-LATER",
                    leg(TOKYO, LOS_ANGELES, "2026-09-05T09:00:00Z", "2026-09-20T09:00:00Z"));

            // 積み替えの上限を緩めても、行って戻る経路は出てこない
            RouteSearchSpecification loose = RouteSearchSpecification.of(TOKYO, LOS_ANGELES,
                    at("2026-09-30T00:00:00Z"), CargoType.GENERAL, 3);

            List<TransitPath> paths = finder.find(loose, List.of(roundTrip, later));

            assertThat(paths).hasSize(1);
            assertThat(paths.get(0).isDirect()).isTrue();
            assertThat(paths.get(0).transitPorts()).isEmpty();
        }

        /**
         * 往復航海で探索が循環しないこと。
         *
         * <p>同じ港へ戻る航海があると、素朴な探索は行き来を繰り返して止まらない。
         */
        @Test
        @DisplayName("往復航海があっても探索は止まる")
        void terminatesWithRoundTrips() {
            Voyage roundTrip = general("V-ROUND",
                    leg(TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-02T09:00:00Z"),
                    leg(BUSAN, TOKYO, "2026-09-03T09:00:00Z", "2026-09-04T09:00:00Z"));
            Voyage out = general("V-OUT",
                    leg(BUSAN, LOS_ANGELES, "2026-09-03T09:00:00Z", "2026-09-18T09:00:00Z"));

            List<TransitPath> paths =
                    finder.find(toLosAngelesBy("2026-09-30T00:00:00Z"), List.of(roundTrip, out));

            assertThat(paths).hasSize(1);
            assertThat(paths.get(0).transitPorts()).containsExactly(BUSAN);
        }
    }

    /**
     * 出発希望日（US10）。
     *
     * <p><strong>探索側の打ち切りは、候補の集合を変えない。</strong>同じ判断を
     * {@link RouteSearchSpecification#isSatisfiedBy} が {@code collect} で必ず行うため、
     * 打ち切りを消しても結果は同じになる（積み替え上限の打ち切りと同じ性質）。したがって
     * 打ち切りそのものを結果で判別することはできない。IT5 のレビューで「消しても緑」と
     * 指摘された点は、**無検査なのではなく観測できない最適化**である。
     *
     * <p>ここで固定するのは<strong>探索の結果として出発希望日が守られること</strong>である。
     * 業務上の下限を守っているのは条件側であり、その検査は
     * {@code RouteSearchSpecificationTest} と {@code FindRouteCandidatesUseCaseTest} にある。
     */
    @Nested
    @DisplayName("出発希望日（US10）")
    class EarliestDeparture {

        private RouteSearchSpecification from(String earliestDeparture) {
            return RouteSearchSpecification.of(TOKYO, LOS_ANGELES,
                    at("2026-09-30T14:59:59Z"), CargoType.GENERAL, 2, at(earliestDeparture));
        }

        @Test
        @DisplayName("出発希望日より前に出る便は候補にならない")
        void doesNotYieldVoyagesDepartingTooEarly() {
            List<Voyage> voyages = List.of(general("V-EARLY",
                    leg(TOKYO, LOS_ANGELES, "2026-09-01T09:00:00Z", "2026-09-20T09:00:00Z")));

            assertThat(finder.find(from("2026-09-05T00:00:00Z"), voyages)).isEmpty();
        }

        @Test
        @DisplayName("出発希望日ちょうどに出る便は候補になる")
        void yieldsVoyageDepartingExactlyOnTheDate() {
            List<Voyage> voyages = List.of(general("V-ON-TIME",
                    leg(TOKYO, LOS_ANGELES, "2026-09-05T00:00:00Z", "2026-09-20T09:00:00Z")));

            assertThat(finder.find(from("2026-09-05T00:00:00Z"), voyages)).hasSize(1);
        }

        /**
         * 積み替えの区間にも効く。
         *
         * <p>最初の区間だけを見る形にすると、乗り継ぎ先が出発希望日より前に出る経路が残る。
         */
        @Test
        @DisplayName("積み替え後の便にも効く")
        void appliesToTransshipmentLegsToo() {
            List<Voyage> voyages = List.of(
                    general("V-FIRST",
                            leg(TOKYO, BUSAN, "2026-09-06T00:00:00Z", "2026-09-08T00:00:00Z")),
                    general("V-SECOND",
                            leg(BUSAN, LOS_ANGELES, "2026-09-09T00:00:00Z",
                                    "2026-09-20T09:00:00Z")));

            // 両方とも 9/6 以降に出るので成立する
            assertThat(finder.find(from("2026-09-05T00:00:00Z"), voyages)).hasSize(1);
            // 9/8 以降を求めると、最初の便が出せず経路そのものが消える
            assertThat(finder.find(from("2026-09-08T00:00:00Z"), voyages)).isEmpty();
        }
    }
}
