package com.example.cargotracker.trackingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EventSource enum")
class EventSourceTest {

    @Test
    @DisplayName("HANDLING / MANUAL / SYSTEM の 3 値が定義されている")
    void hasThreeSources() {
        assertThat(EventSource.values())
                .containsExactly(EventSource.HANDLING, EventSource.MANUAL, EventSource.SYSTEM);
    }
}
