package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.RelaxationRequest;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.RoutingCriteria;
import com.example.cargotracker.routing.domain.model.RoutingWeight;
import com.example.cargotracker.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 探索条件を緩める要求（US10）。 */
@DisplayName("US10 緩和の要求")
class RelaxationRequestTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, java.time.Month.MAY, 1);

    private RoutingCriteria 条件() {
        return RoutingCriteria.of(
                Location.of("JPOSA"), Location.of("USLAX"), DEADLINE,
                RoutingCargoType.GENERAL,
                RoutingWeight.ofKilograms(new BigDecimal("1000")), 2);
    }

    @Test
    void 期限を延ばせる() {
        var relaxed = new RelaxationRequest(7, null).applyTo(条件());

        assertThat(relaxed.arrivalDeadline()).isEqualTo(DEADLINE.plusDays(7));
    }

    /** **延ばした事実そのものを残す。** 消えると荷主に何日延びたか伝えられない（US12）。 */
    @Test
    void 延ばしても当初の期限は変わらない() {
        var relaxed = new RelaxationRequest(7, null).applyTo(条件());

        assertThat(relaxed.originalArrivalDeadline()).isEqualTo(DEADLINE);
        assertThat(relaxed.isDeadlineRelaxed()).isTrue();
    }

    @Test
    void 二度緩めても当初の期限は最初のままである() {
        var once = new RelaxationRequest(3, null).applyTo(条件());
        var twice = new RelaxationRequest(3, null).applyTo(once);

        assertThat(twice.originalArrivalDeadline()).isEqualTo(DEADLINE);
        assertThat(twice.arrivalDeadline()).isEqualTo(DEADLINE.plusDays(6));
    }

    @Test
    void 経由回数の上限を緩められる() {
        var relaxed = new RelaxationRequest(0, 4).applyTo(条件());

        assertThat(relaxed.maxTransitCount()).isEqualTo(4);
        assertThat(relaxed.arrivalDeadline()).isEqualTo(DEADLINE);
    }

    /**
     * <strong>上限を超えた要求は切り詰めず拒否する。</strong>
     *
     * <p>黙って 30 日に丸めると、経路設計者は「400 日で探した結果、候補が無かった」と読む。
     * 要求と違う条件で探した結果を、要求どおりの結果として返さない。
     */
    @Test
    void 上限を超える延長は拒否される() {
        assertThatThrownBy(() -> new RelaxationRequest(400, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("延長できる日数の上限");
    }

    @Test
    void 上限ちょうどは通る() {
        assertThat(new RelaxationRequest(RelaxationRequest.MAX_EXTRA_DAYS, null).extraDays())
                .isEqualTo(RelaxationRequest.MAX_EXTRA_DAYS);
    }

    @Test
    void 経由回数の上限にも上限がある() {
        assertThatThrownBy(() -> new RelaxationRequest(0, 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("経由回数の上限は");
    }

    /** 期限の前倒しは再算出ではない。**縮める操作は US10 の対象外である。** */
    @Test
    void 期限の前倒しはできない() {
        assertThatThrownBy(() -> new RelaxationRequest(-1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 何も緩めない要求は条件を変えない() {
        var criteria = 条件();
        var same = RelaxationRequest.none().applyTo(criteria);

        assertThat(same).isEqualTo(criteria);
        assertThat(RelaxationRequest.none().relaxesAnything()).isFalse();
    }
}
