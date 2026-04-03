package com.example.cargotracker.billing.domain;

import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.valueobjects.ChargeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FreightCharge 集約")
class FreightChargeTest {

    // ── FreightId ────────────────────────────────────────────────────────

    @Test
    @DisplayName("FreightId.generate() は null でない UUID を返す")
    void freightId_generate_returnsNonNull() {
        FreightId id = FreightId.generate();
        assertThat(id).isNotNull();
        assertThat(id.value()).isNotNull();
    }

    @Test
    @DisplayName("FreightId の value が null の場合は IllegalArgumentException をスローする")
    void freightId_nullValue_throwsException() {
        assertThatThrownBy(() -> new FreightId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── calculate (DRAFT 作成) ────────────────────────────────────────────

    @Test
    @DisplayName("calculate で DRAFT 状態の輸送料金を作成できる")
    void calculate_createsDraftFreightCharge() {
        FreightId id = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(id, "BK-001", new BigDecimal("1000"));

        assertThat(charge.getId()).isEqualTo(id);
        assertThat(charge.getBookingId()).isEqualTo("BK-001");
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.DRAFT);
        assertThat(charge.getBaseAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(charge.getAdjustmentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(charge.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    @DisplayName("calculate の totalAmount は baseAmount と等しい")
    void calculate_totalAmountEqualsBaseAmount() {
        FreightCharge charge = FreightCharge.calculate(FreightId.generate(), "BK-002", new BigDecimal("2500"));
        assertThat(charge.getTotalAmount()).isEqualByComparingTo(charge.getBaseAmount());
    }

    // ── applyAdjustment ──────────────────────────────────────────────────

    @Test
    @DisplayName("applyAdjustment で調整額が適用され totalAmount が更新される")
    void applyAdjustment_updatesTotalAmount() {
        FreightCharge charge = FreightCharge.calculate(FreightId.generate(), "BK-003", new BigDecimal("1000"));
        charge.applyAdjustment(new BigDecimal("200"));

        assertThat(charge.getAdjustmentAmount()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(charge.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1200"));
    }

    @Test
    @DisplayName("applyAdjustment でマイナス調整額も適用できる")
    void applyAdjustment_negativeAdjustment() {
        FreightCharge charge = FreightCharge.calculate(FreightId.generate(), "BK-004", new BigDecimal("1000"));
        charge.applyAdjustment(new BigDecimal("-300"));

        assertThat(charge.getTotalAmount()).isEqualByComparingTo(new BigDecimal("700"));
    }

    @Test
    @DisplayName("CONFIRMED 状態で applyAdjustment すると IllegalStateException をスローする")
    void applyAdjustment_confirmed_throwsException() {
        FreightCharge charge = FreightCharge.calculate(FreightId.generate(), "BK-005", new BigDecimal("1000"));
        charge.confirm();

        BigDecimal adjustment = new BigDecimal("100");
        assertThatThrownBy(() -> charge.applyAdjustment(adjustment))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── confirm ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("confirm で DRAFT から CONFIRMED に遷移できる")
    void confirm_transitsToCONFIRMED() {
        FreightCharge charge = FreightCharge.calculate(FreightId.generate(), "BK-006", new BigDecimal("1000"));
        charge.confirm();

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
    }

    @Test
    @DisplayName("CONFIRMED 状態で confirm すると IllegalStateException をスローする")
    void confirm_alreadyConfirmed_throwsException() {
        FreightCharge charge = FreightCharge.calculate(FreightId.generate(), "BK-007", new BigDecimal("1000"));
        charge.confirm();

        assertThatThrownBy(charge::confirm)
                .isInstanceOf(IllegalStateException.class);
    }

    // ── reconstitute ─────────────────────────────────────────────────────

    @Test
    @DisplayName("reconstitute でストアから再構成できる")
    void reconstitute_fromStore() {
        FreightId id = FreightId.generate();
        FreightCharge charge = FreightCharge.reconstitute(
                id, "BK-008", ChargeStatus.CONFIRMED,
                new BigDecimal("1000"), new BigDecimal("200"), new BigDecimal("1200")
        );

        assertThat(charge.getId()).isEqualTo(id);
        assertThat(charge.getBookingId()).isEqualTo("BK-008");
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(charge.getBaseAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(charge.getAdjustmentAmount()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(charge.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1200"));
    }

    // ── バリデーション ─────────────────────────────────────────────────────

    @Test
    @DisplayName("id が null の場合は IllegalArgumentException をスローする")
    void calculate_nullId_throwsException() {
        BigDecimal base = new BigDecimal("1000");
        assertThatThrownBy(() -> FreightCharge.calculate(null, "BK-009", base))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bookingId が null の場合は IllegalArgumentException をスローする")
    void calculate_nullBookingId_throwsException() {
        FreightId id139 = FreightId.generate();
        BigDecimal base139 = new BigDecimal("1000");
        assertThatThrownBy(() -> FreightCharge.calculate(id139, null, base139))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bookingId が空文字の場合は IllegalArgumentException をスローする")
    void calculate_blankBookingId_throwsException() {
        FreightId id146 = FreightId.generate();
        BigDecimal base146 = new BigDecimal("1000");
        assertThatThrownBy(() -> FreightCharge.calculate(id146, "  ", base146))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("baseAmount が null の場合は IllegalArgumentException をスローする")
    void calculate_nullBaseAmount_throwsException() {
        FreightId id153 = FreightId.generate();
        assertThatThrownBy(() -> FreightCharge.calculate(id153, "BK-010", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("baseAmount が 0 以下の場合は IllegalArgumentException をスローする")
    void calculate_nonPositiveBaseAmount_throwsException() {
        FreightId id160 = FreightId.generate();
        assertThatThrownBy(() -> FreightCharge.calculate(id160, "BK-011", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        FreightId id162 = FreightId.generate();
        BigDecimal negOne = new BigDecimal("-1");
        assertThatThrownBy(() -> FreightCharge.calculate(id162, "BK-012", negOne))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
