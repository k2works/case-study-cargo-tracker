package com.example.routingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 経路探索条件（US08）。
 *
 * <p>Booking Context の {@code RouteSpecification}（ルート仕様）とは別の型である。
 * あちらは予約に永続化される輸送の要件で、こちらはその場かぎりの探索条件。
 */
@DisplayName("経路探索条件")
class RouteSearchSpecificationTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Instant DEADLINE = Instant.parse("2026-09-30T14:59:59Z");

    private static RouteSearchSpecification specification() {
        return RouteSearchSpecification.of(TOKYO, LOS_ANGELES, DEADLINE, CargoType.GENERAL);
    }

    private static TransitPath pathArrivingAt(String arrival) {
        return TransitPath.of(List.of(TransitEdge.of(VoyageNumber.of("V0100"), TOKYO, LOS_ANGELES,
                Instant.parse("2026-09-01T09:00:00Z"), Instant.parse(arrival))));
    }

    @Nested
    @DisplayName("受け付けない条件")
    class Invalid {

        /**
         * 探索できない条件は、探索の前に断る。
         *
         * <p>そのまま探索すると結果は必ず 0 件になり、経路設計者には「経路が無い」としか
         * 見えない。指定が誤っていることに気づけない。
         */
        @Test
        @DisplayName("出発地と目的地は必須で、同じにはできない")
        void requiresDistinctEndpoints() {
            assertThatThrownBy(() -> RouteSearchSpecification.of(
                    null, LOS_ANGELES, DEADLINE, CargoType.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RouteSearchSpecification.of(
                    TOKYO, null, DEADLINE, CargoType.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RouteSearchSpecification.of(
                    TOKYO, TOKYO, DEADLINE, CargoType.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("同じにできません");
        }

        @Test
        @DisplayName("到着期限と貨物種別は必須")
        void requiresDeadlineAndCargoType() {
            assertThatThrownBy(() -> RouteSearchSpecification.of(
                    TOKYO, LOS_ANGELES, null, CargoType.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("到着期限");
            assertThatThrownBy(() -> RouteSearchSpecification.of(
                    TOKYO, LOS_ANGELES, DEADLINE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("貨物種別");
        }

        @Test
        @DisplayName("積み替えの上限に負の数は指定できない")
        void rejectsNegativeTransshipmentLimit() {
            assertThatThrownBy(() -> RouteSearchSpecification.of(
                    TOKYO, LOS_ANGELES, DEADLINE, CargoType.GENERAL, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * 上限をいくらでも緩められてはいけない。
         *
         * <p>探索は深さに対して指数的に広がる。外から任意の値を渡せると、1 回の問い合わせで
         * サービスを止められる。業務としても 4 回以上の積み替えを提案する場面が無い。
         */
        @Test
        @DisplayName("積み替えの上限は絶対の上限を超えられない")
        void rejectsTooLooseTransshipmentLimit() {
            int tooMany = RouteSearchSpecification.ABSOLUTE_MAX_TRANSSHIPMENTS + 1;

            assertThatThrownBy(() -> RouteSearchSpecification.of(
                    TOKYO, LOS_ANGELES, DEADLINE, CargoType.GENERAL, tooMany))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("積み替えの上限");
        }

        /** 境界。絶対の上限ちょうどは受け付ける。 */
        @Test
        @DisplayName("絶対の上限ちょうどは受け付ける")
        void acceptsTheAbsoluteLimitItself() {
            assertThat(RouteSearchSpecification.of(TOKYO, LOS_ANGELES, DEADLINE,
                    CargoType.GENERAL, RouteSearchSpecification.ABSOLUTE_MAX_TRANSSHIPMENTS)
                    .maxTransshipments())
                    .isEqualTo(RouteSearchSpecification.ABSOLUTE_MAX_TRANSSHIPMENTS);
        }
    }

    @Nested
    @DisplayName("条件を満たすか")
    class Satisfaction {

        @Test
        @DisplayName("端点が違う経路は満たさない")
        void requiresMatchingEndpoints() {
            RouteSearchSpecification other = RouteSearchSpecification.of(
                    Location.of("CNSHA", "Shanghai"), LOS_ANGELES, DEADLINE, CargoType.GENERAL);

            assertThat(other.isSatisfiedBy(pathArrivingAt("2026-09-15T09:00:00Z"))).isFalse();
        }

        /**
         * 積み替えの上限は<strong>条件側でも単独で</strong>守る。
         *
         * <p>探索側にも打ち切りがあるため、片方だけを壊しても全体は緑になる。2 つのガードが
         * 互いを隠すと、どちらも検証されていない状態になる。ここは経路を直接組み立てて
         * 探索を通さずに確かめる。
         */
        @Test
        @DisplayName("上限を超える積み替えの経路は満たさない")
        void rejectsPathWithTooManyTransshipments() {
            RouteSearchSpecification onlyDirect = RouteSearchSpecification.of(
                    TOKYO, LOS_ANGELES, DEADLINE, CargoType.GENERAL, 0);

            TransitPath viaShanghai = TransitPath.of(List.of(
                    TransitEdge.of(VoyageNumber.of("V-A"), TOKYO, SHANGHAI,
                            Instant.parse("2026-09-01T09:00:00Z"),
                            Instant.parse("2026-09-03T09:00:00Z")),
                    TransitEdge.of(VoyageNumber.of("V-B"), SHANGHAI, LOS_ANGELES,
                            Instant.parse("2026-09-04T09:00:00Z"),
                            Instant.parse("2026-09-18T09:00:00Z"))));

            assertThat(onlyDirect.isSatisfiedBy(viaShanghai)).isFalse();
            // 上限を 1 に緩めれば満たす（上限そのものが効いていることの裏返し）
            assertThat(onlyDirect.withMaxTransshipments(1).isSatisfiedBy(viaShanghai)).isTrue();
        }

        @Test
        @DisplayName("経路が無ければ満たさない")
        void rejectsNull() {
            assertThat(specification().isSatisfiedBy(null)).isFalse();
        }

        @Test
        @DisplayName("期限ちょうどは満たし、1 秒でも過ぎれば満たさない")
        void includesTheDeadlineItself() {
            assertThat(specification().isSatisfiedBy(pathArrivingAt("2026-09-30T14:59:59Z"))).isTrue();
            assertThat(specification().isSatisfiedBy(pathArrivingAt("2026-09-30T15:00:00Z"))).isFalse();
        }
    }

    @Nested
    @DisplayName("条件を緩める")
    class Loosening {

        /** 候補が無かったとき、経路設計者が条件を緩めて探し直すための操作。 */
        @Test
        @DisplayName("積み替えの上限だけを変えた条件を作れる")
        void changesOnlyTheTransshipmentLimit() {
            RouteSearchSpecification loose = specification().withMaxTransshipments(3);

            assertThat(loose.maxTransshipments()).isEqualTo(3);
            assertThat(loose.origin()).isEqualTo(TOKYO);
            assertThat(loose.destination()).isEqualTo(LOS_ANGELES);
            assertThat(loose.arrivalDeadline()).isEqualTo(DEADLINE);
            assertThat(loose.cargoType()).isEqualTo(CargoType.GENERAL);
        }

        @Test
        @DisplayName("到着期限だけを変えた条件を作れる")
        void changesOnlyTheDeadline() {
            Instant later = Instant.parse("2026-10-07T14:59:59Z");
            RouteSearchSpecification extended = specification().withArrivalDeadline(later);

            assertThat(extended.arrivalDeadline()).isEqualTo(later);
            assertThat(extended.maxTransshipments())
                    .isEqualTo(RouteSearchSpecification.DEFAULT_MAX_TRANSSHIPMENTS);
            assertThat(extended.cargoType()).isEqualTo(CargoType.GENERAL);
        }
    }

    /**
     * 条件は丸ごと 1 つの値として比べる。
     *
     * <p>項目ごとの比較を積み上げると、項目が増えるたび同じ漏れが起きる（IT3 の航海差分）。
     */
    @Test
    @DisplayName("同じ内容の条件は等しく、1 つでも違えば等しくない")
    void comparesAsAWhole() {
        assertThat(specification()).isEqualTo(specification());
        assertThat(specification()).hasSameHashCodeAs(specification());

        assertThat(specification()).isNotEqualTo(specification().withMaxTransshipments(3));
        assertThat(specification())
                .isNotEqualTo(specification().withArrivalDeadline(Instant.parse("2026-10-01T00:00:00Z")));
        assertThat(specification()).isNotEqualTo(RouteSearchSpecification.of(
                TOKYO, LOS_ANGELES, DEADLINE, CargoType.HAZARDOUS));
    }
}
