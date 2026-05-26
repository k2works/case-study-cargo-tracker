package com.example.routingms.application;

import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.domain.model.RouteLeg;
import com.example.routingms.infrastructure.repositories.mybatis.RouteDesignRequestMapper;
import com.example.shared.events.RouteConfirmedEvent;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 経路確定（cross-service 紐付け）アプリケーションサービス（US11）のユニットテスト。
 */
@ExtendWith(MockitoExtension.class)
class RouteConfirmationServiceTest {

    @Mock
    private RouteCalculationService calculationService;

    @Mock
    private RouteDesignRequestMapper requestMapper;

    @Mock
    private EventGateway eventGateway;

    @InjectMocks
    private RouteConfirmationService service;

    private static RouteCandidate transshipment() {
        return new RouteCandidate(List.of(
                new RouteLeg("V-A", "JPTYO", "SGSIN",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 10, 18, 0)),
                new RouteLeg("V-B", "SGSIN", "DEHAM",
                        LocalDateTime.of(2026, 7, 12, 9, 0), LocalDateTime.of(2026, 7, 30, 18, 0))),
                27, new BigDecimal("1480000"), "JPY");
    }

    @Test
    void 経路を確定すると確定イベントを発行し依頼を割当済みにする() {
        when(calculationService.calculate("B-001")).thenReturn(List.of(transshipment()));

        RouteCandidate confirmed = service.confirmRoute("B-001", 1);

        assertThat(confirmed.voyageNumbers()).containsExactly("V-A", "V-B");

        ArgumentCaptor<RouteConfirmedEvent> captor = ArgumentCaptor.forClass(RouteConfirmedEvent.class);
        verify(eventGateway).publish(captor.capture());
        RouteConfirmedEvent event = captor.getValue();
        assertThat(event.bookingId()).isEqualTo("B-001");
        assertThat(event.legs()).hasSize(2);
        assertThat(event.legs().get(0).voyageNumber()).isEqualTo("V-A");
        assertThat(event.legs().get(0).loadUnlocode()).isEqualTo("JPTYO");
        assertThat(event.legs().get(0).unloadUnlocode()).isEqualTo("SGSIN");
        assertThat(event.legs().get(1).voyageNumber()).isEqualTo("V-B");
        assertThat(event.legs().get(1).unloadUnlocode()).isEqualTo("DEHAM");

        verify(requestMapper).updateStatus("B-001", "ASSIGNED");
    }

    @Test
    void 候補がない場合は確定できずイベントを発行しない() {
        when(calculationService.calculate("B-002")).thenReturn(List.of());

        assertThatThrownBy(() -> service.confirmRoute("B-002", 1))
                .isInstanceOf(IllegalArgumentException.class);

        verify(eventGateway, never()).publish(any(RouteConfirmedEvent.class));
        verify(requestMapper, never()).updateStatus(any(), any());
    }
}
