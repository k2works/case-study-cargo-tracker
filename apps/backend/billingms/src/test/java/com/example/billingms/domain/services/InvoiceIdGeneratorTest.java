package com.example.billingms.domain.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InvoiceIdGenerator} の単体テスト（IT7 review M1 architect 対応 / ADR-0012）。
 *
 * <p>同一 bookingId から派生される invoiceId が決定論的に同一であること、異なる bookingId からは
 * 異なる invoiceId が派生されること、UUID 形式であることを検証する。</p>
 */
class InvoiceIdGeneratorTest {

    private final InvoiceIdGenerator generator = new InvoiceIdGenerator();

    @Test
    @DisplayName("review M1: 同一 bookingId からは決定論的に同じ invoiceId を派生する（リプレイ冪等）")
    void 決定論性() {
        String first = generator.fromBookingId("B-001");
        String second = generator.fromBookingId("B-001");

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("review M1: 異なる bookingId からは異なる invoiceId を派生する")
    void 異なるbookingIdは異なるinvoiceId() {
        String first = generator.fromBookingId("B-001");
        String second = generator.fromBookingId("B-002");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("review M1: 派生 invoiceId は UUID 形式（version 5 風）")
    void UUID形式() {
        String result = generator.fromBookingId("B-001");

        UUID uuid = UUID.fromString(result);
        assertThat(uuid.version()).isEqualTo(5);
        // variant 2（RFC 4122）
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("review M1: bookingId が null だと IllegalArgumentException")
    void bookingIdがnullで例外() {
        assertThatThrownBy(() -> generator.fromBookingId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("review M1: bookingId が空文字だと IllegalArgumentException")
    void bookingIdが空文字で例外() {
        assertThatThrownBy(() -> generator.fromBookingId("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
