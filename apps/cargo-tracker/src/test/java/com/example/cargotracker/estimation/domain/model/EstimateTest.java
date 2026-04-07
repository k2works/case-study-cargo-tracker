package com.example.cargotracker.estimation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Estimate ドメインモデルテスト")
class EstimateTest {

    @Test
    @DisplayName("有効なパラメータで Estimate を作成できる")
    void shouldCreateEstimateWithValidParameters() {
        Estimate estimate = Estimate.create(
                "JPTYO",
                "USNYC",
                LocalDate.now().plusDays(30),
                "GENERAL",
                new BigDecimal("1000.000")
        );

        assertThat(estimate.getEstimateId()).isNotNull();
        assertThat(estimate.getOriginUnlocode()).isEqualTo("JPTYO");
        assertThat(estimate.getDestinationUnlocode()).isEqualTo("USNYC");
        assertThat(estimate.getCargoType()).isEqualTo("GENERAL");
        assertThat(estimate.getWeightKg()).isEqualByComparingTo(new BigDecimal("1000.000"));
        assertThat(estimate.getStatus()).isEqualTo(EstimateStatus.CREATED);
        assertThat(estimate.getCandidates()).isEmpty();
    }

    @Test
    @DisplayName("出発地が null の場合は例外が発生する")
    void shouldThrowWhenOriginIsNull() {
        assertThatThrownBy(() -> Estimate.create(
                null,
                "USNYC",
                LocalDate.now().plusDays(30),
                "GENERAL",
                new BigDecimal("1000.000")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("出発地と目的地が同一の場合は例外が発生する")
    void shouldThrowWhenOriginEqualsDestination() {
        assertThatThrownBy(() -> Estimate.create(
                "JPTYO",
                "JPTYO",
                LocalDate.now().plusDays(30),
                "GENERAL",
                new BigDecimal("1000.000")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("重量が 0 以下の場合は例外が発生する")
    void shouldThrowWhenWeightIsZeroOrNegative() {
        assertThatThrownBy(() -> Estimate.create(
                "JPTYO",
                "USNYC",
                LocalDate.now().plusDays(30),
                "GENERAL",
                BigDecimal.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ルート候補を追加できる")
    void shouldAddRouteCandidates() {
        Estimate estimate = Estimate.create(
                "JPTYO",
                "USNYC",
                LocalDate.now().plusDays(30),
                "GENERAL",
                new BigDecimal("1000.000")
        );

        List<RouteCandidate> candidates = List.of(
                new RouteCandidate("V001", "SGSIN", 21, new BigDecimal("500000.00")),
                new RouteCandidate("V002", "HKHKG", 28, new BigDecimal("480000.00"))
        );
        estimate.addCandidates(candidates);

        assertThat(estimate.getCandidates()).hasSize(2);
        assertThat(estimate.getCandidates().get(0).voyageNumber()).isEqualTo("V001");
    }
}
