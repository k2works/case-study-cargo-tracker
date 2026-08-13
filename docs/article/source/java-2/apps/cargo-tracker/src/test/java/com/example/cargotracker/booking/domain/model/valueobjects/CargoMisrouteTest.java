package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("誤配の記録（CargoMisroute）")
class CargoMisrouteTest {

    private static final MisrouteDetection 写し =
            new MisrouteDetection(Location.of("JPTYO"), Instant.parse("2026-08-01T03:00:00Z"));

    @Test
    void 誤配になっていない状態は写しを持たない() {
        assertThat(CargoMisroute.none().detection()).isNull();
        assertThat(CargoMisroute.none().detected()).isFalse();
        assertThat(CargoMisroute.none().hasDetection()).isFalse();
    }

    @Test
    void 写しを伴う誤配を記録できる() {
        CargoMisroute misroute = CargoMisroute.detected(写し);

        assertThat(misroute.detection()).isEqualTo(写し);
        assertThat(misroute.detected()).isTrue();
        assertThat(misroute.hasDetection()).isTrue();
    }

    /**
     * <strong>写しが無い誤配を拒まない。</strong>
     *
     * <p>荷役のイベントは場所か日時のどちらかを欠くことがあり、
     * {@code MisrouteDetection.reconstruct} はそのとき {@code null} を返す。
     * ここで拒むと、<strong>誤配の反映そのものが例外で止まる</strong> ——
     * 経路状態が {@code MISROUTED} にならず、現場は誤配に気づけないまま輸送が続く。
     *
     * <p><strong>写しは「予約詳細で現在地を示すための付録」であり、
     * 誤配という事実の成立条件ではない。</strong>
     */
    @Test
    void 写しが無くても誤配は記録できる() {
        CargoMisroute misroute = CargoMisroute.detected(null);

        assertThat(misroute.detected())
                .as("**誤配になったことは記録されている**")
                .isTrue();
        assertThat(misroute.hasDetection())
                .as("**ただし、どこで・いつ検知したかは分からない**")
                .isFalse();
    }

    /** <strong>写しだけを持つ「誤配でない」状態は作れない。</strong> */
    @Test
    void 誤配でないのに写しを持つことはできない() {
        assertThatThrownBy(() -> new CargoMisroute(false, 写し))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("誤配になっていないのに");
    }
}
