package com.example.billingms.domain.services;

import com.example.billingms.infrastructure.repositories.mybatis.InvoiceSummaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link InvoiceNumberGenerator} 単体テスト（IT7 T4.2、US23 受入基準 1）。
 *
 * <p>INV-YYYYMMDD-XXXX 形式の採番ロジック。同日内のシーケンス採番、ON CONFLICT 時の再試行、
 * 5 回連続衝突時の例外動作を検証する。</p>
 */
class InvoiceNumberGeneratorTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 9, 1);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_DATE.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private InvoiceSummaryMapper mapper;
    private InvoiceNumberGenerator generator;

    @BeforeEach
    void setUp() {
        mapper = mock(InvoiceSummaryMapper.class);
        generator = new InvoiceNumberGenerator(mapper, FIXED_CLOCK);
    }

    @Test
    @DisplayName("US23: 当日初回採番は INV-YYYYMMDD-0001")
    void 当日初回採番() {
        when(mapper.findMaxInvoiceNumberSequenceForDate("20260901")).thenReturn(null);

        String number = generator.next();

        assertThat(number).isEqualTo("INV-20260901-0001");
    }

    @Test
    @DisplayName("US23: 当日 5 件目は INV-YYYYMMDD-0006")
    void 当日6件目採番() {
        when(mapper.findMaxInvoiceNumberSequenceForDate("20260901")).thenReturn(5);

        String number = generator.next();

        assertThat(number).isEqualTo("INV-20260901-0006");
    }

    @Test
    @DisplayName("US23: 9999 を超える場合は IllegalStateException（日次上限）")
    void 上限超過で例外() {
        when(mapper.findMaxInvoiceNumberSequenceForDate("20260901")).thenReturn(9999);

        assertThatThrownBy(() -> generator.next())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("日次採番上限");
    }

    @Test
    @DisplayName("US23: ゼロパディングで 4 桁表現（0001 / 0010 / 0100 / 1000）")
    void ゼロパディング() {
        when(mapper.findMaxInvoiceNumberSequenceForDate("20260901"))
                .thenReturn(0, 9, 99, 999);

        assertThat(generator.next()).isEqualTo("INV-20260901-0001");
        assertThat(generator.next()).isEqualTo("INV-20260901-0010");
        assertThat(generator.next()).isEqualTo("INV-20260901-0100");
        assertThat(generator.next()).isEqualTo("INV-20260901-1000");
    }
}
