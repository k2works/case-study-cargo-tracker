package com.example.billingms.domain.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InvoiceNumberGenerator} 単体テスト（IT7 T4.2 / review M2 リファクタ後）。
 *
 * <p>INV-YYYYMMDD-XXXX 形式の採番ロジック。同日内のシーケンス採番、当日初回、9999 超過時の
 * 例外、ゼロパディング表現を検証する。Mapper モックに依存せず、インメモリ Fake repository
 * （{@link FakeInvoiceNumberSequenceRepository}）で完結する（DIP 効果）。</p>
 */
class InvoiceNumberGeneratorTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 9, 1);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_DATE.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private FakeInvoiceNumberSequenceRepository repository;
    private InvoiceNumberGenerator generator;

    @BeforeEach
    void setUp() {
        repository = new FakeInvoiceNumberSequenceRepository();
        generator = new InvoiceNumberGenerator(repository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("US23: 当日初回採番は INV-YYYYMMDD-0001")
    void 当日初回採番() {
        // 当日採番なし（null 返却）

        String number = generator.next();

        assertThat(number).isEqualTo("INV-20260901-0001");
    }

    @Test
    @DisplayName("US23: 当日 5 件目は INV-YYYYMMDD-0006")
    void 当日6件目採番() {
        repository.set("20260901", 5);

        String number = generator.next();

        assertThat(number).isEqualTo("INV-20260901-0006");
    }

    @Test
    @DisplayName("US23: 9999 を超える場合は IllegalStateException（日次上限）")
    void 上限超過で例外() {
        repository.set("20260901", 9999);

        assertThatThrownBy(() -> generator.next())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("日次採番上限");
    }

    @Test
    @DisplayName("US23: ゼロパディングで 4 桁表現（0001 / 0010 / 0100 / 1000）")
    void ゼロパディング() {
        repository.set("20260901", 0);
        assertThat(generator.next()).isEqualTo("INV-20260901-0001");

        repository.set("20260901", 9);
        assertThat(generator.next()).isEqualTo("INV-20260901-0010");

        repository.set("20260901", 99);
        assertThat(generator.next()).isEqualTo("INV-20260901-0100");

        repository.set("20260901", 999);
        assertThat(generator.next()).isEqualTo("INV-20260901-1000");
    }

    /** インメモリ Fake 実装。テストはこの実装を直接注入することで Mapper / DB に依存しない。 */
    static class FakeInvoiceNumberSequenceRepository implements InvoiceNumberSequenceRepository {
        private final Map<String, Integer> sequences = new HashMap<>();

        public void set(String yyyymmdd, int seq) {
            sequences.put(yyyymmdd, seq);
        }

        @Override
        public Integer findMaxSequenceForDate(String yyyymmdd) {
            return sequences.get(yyyymmdd);
        }
    }
}
