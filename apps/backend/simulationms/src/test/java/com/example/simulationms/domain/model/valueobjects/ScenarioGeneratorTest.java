package com.example.simulationms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 種から作る乱数の並び（US37・[ADR-031] 決定 1）。
 *
 * <p><strong>種を残さないランダム実行は、落ちたときに再現できない。</strong>
 * 「たまに落ちるテストは 2 回目で追う」と決めても、追う手段が無ければ再実行して
 * 通ったことを理由に見送ることになる。種を記録して初めて、ランダム実行は
 * 欠陥の報告手段になる。
 */
@DisplayName("シナリオの生成")
class ScenarioGeneratorTest {

    private static final Seed SEED = Seed.of(20261116L);

    @Nested
    @DisplayName("再現性")
    class Reproducibility {

        @Test
        @DisplayName("同じ種からは、同じ並びが出る")
        void theSameSeedYieldsTheSameSequence() {
            assertThat(take(SEED, 20)).isEqualTo(take(SEED, 20));
        }

        @Test
        @DisplayName("違う種からは、違う並びが出る")
        void differentSeedsYieldDifferentSequences() {
            assertThat(take(SEED, 20)).isNotEqualTo(take(Seed.of(20261117L), 20));
        }

        /**
         * <strong>並行して取り出しても、並びは変わらない</strong>（決定 1）。
         *
         * <p>乱数器を共有すると、取り出す順序で並びが変わる。種を指定しても
         * 再現できなくなり、記録した種が意味を失う。
         */
        @Test
        @DisplayName("並行して作っても、同じ種からは同じ並びが出る")
        void concurrentGeneratorsFromTheSameSeedAgree() throws Exception {
            int concurrency = 8;
            List<String> expected = take(SEED, 20);
            CyclicBarrier startTogether = new CyclicBarrier(concurrency);

            List<List<String>> results;
            try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
                List<Callable<List<String>>> tasks = new ArrayList<>();
                for (int i = 0; i < concurrency; i++) {
                    tasks.add(() -> {
                        startTogether.await();
                        return take(SEED, 20);
                    });
                }
                results = new ArrayList<>();
                for (Future<List<String>> future : pool.invokeAll(tasks)) {
                    results.add(future.get());
                }
            }

            assertThat(results).allSatisfy(actual -> assertThat(actual).isEqualTo(expected));
        }

        private static List<String> take(Seed seed, int count) {
            ScenarioGenerator generator = seed.newGenerator(BigDecimal.valueOf(0.4));
            List<String> requests = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                requests.add(generator.next().toString());
            }
            return requests;
        }
    }

    @Nested
    @DisplayName("生成するもの")
    class Generated {

        private final ScenarioGenerator generator =
                SEED.newGenerator(BigDecimal.valueOf(0.4));

        @Test
        @DisplayName("出発地と目的地は必ず違う")
        void originAndDestinationDiffer() {
            for (int i = 0; i < 200; i++) {
                ScenarioRequest request = generator.next();
                assertThat(request.origin()).isNotEqualTo(request.destination());
            }
        }

        @Test
        @DisplayName("実行できるシナリオしか選ばない")
        void picksOnlyRunnableScenarios() {
            List<String> known = Scenario.all().stream().map(Scenario::id).toList();

            for (int i = 0; i < 200; i++) {
                assertThat(known).contains(generator.next().scenario().id());
            }
        }

        @Test
        @DisplayName("重量と期限は業務として成り立つ範囲に収まる")
        void weightAndDeadlineStayWithinBusinessRange() {
            for (int i = 0; i < 200; i++) {
                ScenarioRequest request = generator.next();
                assertThat(request.weightKg()).isBetween(1, 30_000);
                assertThat(request.deadlineDays()).isBetween(30, 180);
            }
        }
    }

    @Nested
    @DisplayName("例外の割合")
    class ExceptionRatio {

        /**
         * <strong>割合は守られる。</strong>設定した比率を超えて例外を起こすと、
         * 「実運用に近い状態」ではなくなる——実運用で半分が誤配することはない。
         */
        @Test
        @DisplayName("例外シナリオの割合は、設定した比率のあたりに収まる")
        void keepsTheConfiguredExceptionRatio() {
            ScenarioGenerator generator = SEED.newGenerator(BigDecimal.valueOf(0.3));

            int exceptions = 0;
            int total = 2000;
            for (int i = 0; i < total; i++) {
                if (!"standard-transport".equals(generator.next().scenario().id())) {
                    exceptions++;
                }
            }

            assertThat((double) exceptions / total).isBetween(0.25, 0.35);
        }

        @Test
        @DisplayName("比率 0 なら、例外シナリオは選ばれない")
        void ratioZeroNeverPicksAnException() {
            ScenarioGenerator generator = SEED.newGenerator(BigDecimal.ZERO);

            for (int i = 0; i < 200; i++) {
                assertThat(generator.next().scenario().id()).isEqualTo("standard-transport");
            }
        }

        @Test
        @DisplayName("比率 1 なら、正常系は選ばれない")
        void ratioOneAlwaysPicksAnException() {
            ScenarioGenerator generator = SEED.newGenerator(BigDecimal.ONE);

            for (int i = 0; i < 200; i++) {
                assertThat(generator.next().scenario().id()).isNotEqualTo("standard-transport");
            }
        }

        @Test
        @DisplayName("0 から 1 の外の比率は断る")
        void rejectsARatioOutsideZeroToOne() {
            assertThatThrownBy(() -> SEED.newGenerator(BigDecimal.valueOf(1.5)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("例外の割合");
            assertThatThrownBy(() -> SEED.newGenerator(BigDecimal.valueOf(-0.1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("種そのもの")
    class Seeds {

        /**
         * <strong>種は人が読んで写せる形で持つ。</strong>落ちた実行を再現するとき、
         * 画面から読み取って指定する。
         */
        @Test
        @DisplayName("種は数値として読み書きできる")
        void seedIsReadableAndWritable() {
            assertThat(Seed.of(42L).value()).isEqualTo(42L);
        }

        @Test
        @DisplayName("種を指定しなければ、その場で作る")
        void generatesASeedWhenNoneIsGiven() {
            assertThat(Seed.random()).isNotNull();
        }
    }
}
