package com.example.routingms.domain.model;

import com.example.routingms.domain.model.entities.CarrierMovement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CarrierMovement エンティティテスト")
class CarrierMovementTest {

    private static final ZonedDateTime DEP = ZonedDateTime.parse("2025-01-10T08:00:00+09:00");
    private static final ZonedDateTime ARR = ZonedDateTime.parse("2025-01-12T18:00:00+09:00");

    @Test
    @DisplayName("有効なデータで CarrierMovement を生成できること")
    void shouldCreateCarrierMovement() {
        CarrierMovement cm = new CarrierMovement("JPTYO", "CNSHA", DEP, ARR, 0);
        assertThat(cm.getDepartureLocationUnlocode()).isEqualTo("JPTYO");
        assertThat(cm.getArrivalLocationUnlocode()).isEqualTo("CNSHA");
        assertThat(cm.getDepartureDate()).isEqualTo(DEP);
        assertThat(cm.getArrivalDate()).isEqualTo(ARR);
        assertThat(cm.getSeqNumber()).isZero();
    }

    @Test
    @DisplayName("出発地が空の場合は例外をスローすること")
    void shouldThrowWhenDepartureBlank() {
        assertThatThrownBy(() -> new CarrierMovement("", "CNSHA", DEP, ARR, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("到着日が出発日より前の場合は例外をスローすること")
    void shouldThrowWhenArrivalBeforeDeparture() {
        assertThatThrownBy(() -> new CarrierMovement("JPTYO", "CNSHA", ARR, DEP, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同じ値の CarrierMovement は等価であること")
    void shouldBeEqual() {
        CarrierMovement cm1 = new CarrierMovement("JPTYO", "CNSHA", DEP, ARR, 0);
        CarrierMovement cm2 = new CarrierMovement("JPTYO", "CNSHA", DEP, ARR, 0);
        assertThat(cm1).isEqualTo(cm2);
    }
}
