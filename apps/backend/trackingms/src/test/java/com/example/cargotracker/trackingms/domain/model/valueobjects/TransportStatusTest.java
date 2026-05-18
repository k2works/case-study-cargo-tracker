package com.example.cargotracker.trackingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TransportStatus enum")
class TransportStatusTest {

    @Test
    @DisplayName("計画書に定義された 9 つの状態が定義されている")
    void hasNineStates() {
        assertThat(TransportStatus.values())
                .containsExactly(
                        TransportStatus.NOT_RECEIVED,
                        TransportStatus.RECEIVED,
                        TransportStatus.LOADED,
                        TransportStatus.IN_TRANSIT,
                        TransportStatus.UNLOADED,
                        TransportStatus.AWAITING_CLAIM,
                        TransportStatus.DELIVERED,
                        TransportStatus.MISROUTED,
                        TransportStatus.EXCEPTION);
    }

    @Test
    @DisplayName("DELIVERED は終了状態を表す")
    void deliveredIsTerminal() {
        assertThat(TransportStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(TransportStatus.IN_TRANSIT.isTerminal()).isFalse();
    }
}
