package com.example.cargotracker.booking.domain;

import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransportCondition 値オブジェクト")
class TransportConditionTest {

    private static final LocalDate PICKUP = LocalDate.of(2025, 8, 1);
    private static final LocalDate DELIVERY = LocalDate.of(2025, 9, 1);

    @Test
    @DisplayName("希望着日が希望引渡日と同日の場合は例外を投げる")
    void rejectSameDates() {
        assertThatThrownBy(() -> createTransportCondition("JPTYO", "USNYC", PICKUP, PICKUP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("希望着日");
    }

    @Test
    @DisplayName("希望着日が希望引渡日より前の場合は例外を投げる")
    void rejectDeliveryBeforePickup() {
        LocalDate deliveryDate = PICKUP.minusDays(1);
        assertThatThrownBy(() -> createTransportCondition("JPTYO", "USNYC", PICKUP, deliveryDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("希望着日");
    }

    @Test
    @DisplayName("正常な値で生成できる")
    void createValidCondition() {
        TransportCondition condition = createTransportCondition("JPTYO", "USNYC", PICKUP, DELIVERY);

        assertThat(condition.originLocation()).isEqualTo("JPTYO");
        assertThat(condition.destinationLocation()).isEqualTo("USNYC");
        assertThat(condition.requestedPickupDate()).isEqualTo(PICKUP);
        assertThat(condition.requestedDeliveryDate()).isEqualTo(DELIVERY);
    }

    @Test
    @DisplayName("出発地が空の場合は例外を投げる")
    void rejectBlankOrigin() {
        assertThatThrownBy(() -> createTransportCondition("", "USNYC", PICKUP, DELIVERY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("目的地が空の場合は例外を投げる")
    void rejectBlankDestination() {
        assertThatThrownBy(() -> createTransportCondition("JPTYO", "", PICKUP, DELIVERY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TransportCondition createTransportCondition(
            String origin,
            String destination,
            LocalDate pickup,
            LocalDate delivery
    ) {
        return new TransportCondition(origin, destination, pickup, delivery);
    }
}
