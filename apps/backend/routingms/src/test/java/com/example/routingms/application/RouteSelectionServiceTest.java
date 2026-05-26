package com.example.routingms.application;

import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.domain.model.RouteLeg;
import com.example.routingms.infrastructure.repositories.mybatis.RouteDesignRequestMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * 経路選択確定アプリケーションサービス（US09）のユニットテスト。
 */
@ExtendWith(MockitoExtension.class)
class RouteSelectionServiceTest {

    @Mock
    private RouteCalculationService calculationService;

    @Mock
    private RouteDesignRequestMapper requestMapper;

    @InjectMocks
    private RouteSelectionService service;

    private static RouteCandidate direct() {
        return new RouteCandidate(List.of(
                new RouteLeg("V-DIRECT", "JPTYO", "DEHAM",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0))),
                25, new BigDecimal("1200000"), "JPY");
    }

    private static RouteCandidate transshipment() {
        return new RouteCandidate(List.of(
                new RouteLeg("V-A", "JPTYO", "SGSIN",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 10, 18, 0)),
                new RouteLeg("V-B", "SGSIN", "DEHAM",
                        LocalDateTime.of(2026, 7, 12, 9, 0), LocalDateTime.of(2026, 7, 30, 18, 0))),
                27, new BigDecimal("1480000"), "JPY");
    }

    @Test
    void 候補を選択すると経路設計依頼の状態が選択済みになる() {
        when(calculationService.calculate("B-001")).thenReturn(List.of(direct(), transshipment()));

        RouteCandidate selected = service.selectRoute("B-001", 2);

        assertThat(selected.voyageNumbers()).containsExactly("V-A", "V-B");
        verify(requestMapper).updateStatus("B-001", "ROUTE_SELECTED");
    }

    @Test
    void 範囲外の候補番号を選択すると例外を投げる() {
        when(calculationService.calculate("B-001")).thenReturn(List.of(direct()));

        assertThatThrownBy(() -> service.selectRoute("B-001", 2))
                .isInstanceOf(IllegalArgumentException.class);
        verify(requestMapper, never()).updateStatus(any(), any());
    }

    @Test
    void 候補が無い場合は選択できない() {
        when(calculationService.calculate("B-002")).thenReturn(List.of());

        assertThatThrownBy(() -> service.selectRoute("B-002", 1))
                .isInstanceOf(IllegalArgumentException.class);
        verify(requestMapper, never()).updateStatus(any(), any());
    }
}
