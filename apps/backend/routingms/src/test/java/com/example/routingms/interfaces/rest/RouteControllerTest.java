package com.example.routingms.interfaces.rest;

import com.example.routingms.application.RouteCalculationService;
import com.example.routingms.application.RouteConfirmationService;
import com.example.routingms.application.RouteSelectionService;
import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.domain.model.RouteLeg;
import com.example.routingms.interfaces.rest.dto.RouteCandidateResponse;
import com.example.routingms.interfaces.rest.dto.SelectRouteRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 経路候補算出 REST Controller（US08）のユニットテスト。
 */
@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    @Mock
    private RouteCalculationService calculationService;

    @Mock
    private RouteSelectionService selectionService;

    @Mock
    private RouteConfirmationService confirmationService;

    @InjectMocks
    private RouteController controller;

    private static RouteCandidate directCandidate() {
        return new RouteCandidate(List.of(
                new RouteLeg("V-DIRECT", "JPTYO", "DEHAM",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0))),
                25, new BigDecimal("1200000"), "JPY");
    }

    private static RouteCandidate transshipmentCandidate() {
        return new RouteCandidate(List.of(
                new RouteLeg("V-A", "JPTYO", "SGSIN",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 10, 18, 0)),
                new RouteLeg("V-B", "SGSIN", "DEHAM",
                        LocalDateTime.of(2026, 7, 12, 9, 0), LocalDateTime.of(2026, 7, 30, 18, 0))),
                27, new BigDecimal("1480000"), "JPY");
    }

    @Test
    void 経路候補を推奨順に算出し先頭を推奨として返す() {
        when(calculationService.calculate("B-001"))
                .thenReturn(List.of(directCandidate(), transshipmentCandidate()));

        ResponseEntity<List<RouteCandidateResponse>> response = controller.calculate("B-001", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);

        RouteCandidateResponse top = response.getBody().get(0);
        assertThat(top.sequence()).isEqualTo(1);
        assertThat(top.recommended()).isTrue();
        assertThat(top.direct()).isTrue();
        assertThat(top.voyageNumbers()).containsExactly("V-DIRECT");
        assertThat(top.ports()).containsExactly("JPTYO", "DEHAM");
        assertThat(top.estimatedDays()).isEqualTo(25);
        assertThat(top.estimatedCost()).isEqualByComparingTo("1200000");
        assertThat(top.currency()).isEqualTo("JPY");
        assertThat(top.legs()).hasSize(1);

        RouteCandidateResponse second = response.getBody().get(1);
        assertThat(second.sequence()).isEqualTo(2);
        assertThat(second.recommended()).isFalse();
        assertThat(second.direct()).isFalse();
        assertThat(second.voyageNumbers()).containsExactly("V-A", "V-B");
        assertThat(second.ports()).containsExactly("JPTYO", "SGSIN", "DEHAM");
    }

    @Test
    void 期限内到達可能な候補がない場合は空リストを返す() {
        when(calculationService.calculate("B-002")).thenReturn(List.of());

        ResponseEntity<List<RouteCandidateResponse>> response = controller.calculate("B-002", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void US10_到着期限を調整して再算出できる() {
        java.time.LocalDate newDeadline = java.time.LocalDate.of(2026, 10, 31);
        when(calculationService.calculate("B-001", newDeadline)).thenReturn(List.of(directCandidate()));

        ResponseEntity<List<RouteCandidateResponse>> response =
                controller.calculate("B-001", new com.example.routingms.interfaces.rest.dto.CalculateRoutesRequest(newDeadline));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(calculationService).calculate("B-001", newDeadline);
    }

    @Test
    void 経路候補一覧を取得できる() {
        when(calculationService.calculate("B-001")).thenReturn(List.of(directCandidate()));

        ResponseEntity<List<RouteCandidateResponse>> response = controller.candidates("B-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).recommended()).isTrue();
    }

    @Test
    void 経路候補を選択して確定できる() {
        when(selectionService.selectRoute("B-001", 2)).thenReturn(transshipmentCandidate());

        ResponseEntity<RouteCandidateResponse> response =
                controller.select("B-001", new SelectRouteRequest(2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().sequence()).isEqualTo(2);
        assertThat(response.getBody().voyageNumbers()).containsExactly("V-A", "V-B");
        assertThat(response.getBody().ports()).containsExactly("JPTYO", "SGSIN", "DEHAM");
    }

    @Test
    void 確定経路を予約に紐付けると202を返す() {
        when(confirmationService.confirmRoute("B-001", 1)).thenReturn(directCandidate());

        ResponseEntity<RouteCandidateResponse> response =
                controller.confirm("B-001", new SelectRouteRequest(1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().voyageNumbers()).containsExactly("V-DIRECT");
    }
}
