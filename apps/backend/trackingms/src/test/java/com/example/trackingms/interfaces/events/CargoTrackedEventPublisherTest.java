package com.example.trackingms.interfaces.events;

import com.example.shared.events.CargoTrackedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link CargoTrackedEventPublisher} のユニットテスト（IT5 1.4）。
 */
class CargoTrackedEventPublisherTest {

    private EventGateway eventGateway;
    private CargoTrackedEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventGateway = mock(EventGateway.class);
        publisher = new CargoTrackedEventPublisher(eventGateway);
    }

    @Test
    @DisplayName("US14: TrackingInitializedEvent を受けて CargoTrackedEvent を cross-service publish")
    void 採番完了イベントを発行する() {
        publisher.on(new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"));

        verify(eventGateway).publish(eq(new CargoTrackedEvent("B-001", "TRK-AB12CD3456")));
    }
}
