package com.example.routingms.application;

import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.domain.projections.RouteDesignRequestProjection;
import com.example.routingms.domain.projections.VoyageProjection;
import com.example.routingms.domain.services.OptimalRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 経路候補算出アプリケーションサービス（US08）のユニットテスト。
 *
 * <p>{@link RouteCalculationService} は経路設計依頼（route_design_request Read Model）から
 * 探索制約を組み立て、航海スケジュール一覧を取得して {@link OptimalRouteService} に委譲する。
 * Mapper をモック化し、依頼解決と委譲を検証する。</p>
 */
@ExtendWith(MockitoExtension.class)
class RouteCalculationServiceTest {

    @Mock
    private RouteDesignRequestQueryService requestQueryService;

    @Mock
    private VoyageQueryService voyageQueryService;

    private RouteCalculationService service;

    @BeforeEach
    void setUp() {
        service = new RouteCalculationService(
                requestQueryService, voyageQueryService, new OptimalRouteService());
    }

    private static RouteDesignRequestProjection request(String bookingId) {
        RouteDesignRequestProjection r = new RouteDesignRequestProjection();
        r.setBookingId(bookingId);
        r.setOriginUnlocode("JPTYO");
        r.setDestinationUnlocode("DEHAM");
        r.setArrivalDeadline(LocalDate.of(2026, 8, 1));
        r.setCargoType("GENERAL");
        r.setStatus("PENDING");
        r.setRequestedAt(LocalDateTime.of(2026, 6, 1, 9, 0));
        return r;
    }

    private static VoyageProjection voyage(String number, String origin, String dest,
                                           LocalDateTime departure, LocalDateTime arrival) {
        VoyageProjection v = new VoyageProjection();
        v.setVoyageNumber(number);
        v.setOriginUnlocode(origin);
        v.setDestUnlocode(dest);
        v.setDepartureDate(departure);
        v.setArrivalDate(arrival);
        v.setAcceptedCargoTypes(List.of("GENERAL"));
        return v;
    }

    @Test
    void 経路設計依頼から経路候補を算出する() {
        when(requestQueryService.findByBookingId("B-001")).thenReturn(request("B-001"));
        when(voyageQueryService.findAll()).thenReturn(List.of(
                voyage("V-DIRECT", "JPTYO", "DEHAM",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0))));

        List<RouteCandidate> candidates = service.calculate("B-001");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).isDirect()).isTrue();
        assertThat(candidates.get(0).voyageNumbers()).containsExactly("V-DIRECT");
    }

    @Test
    void 期限内到達可能な航海がない場合は空の候補を返す() {
        when(requestQueryService.findByBookingId("B-002")).thenReturn(request("B-002"));
        when(voyageQueryService.findAll()).thenReturn(List.of(
                voyage("V-LATE", "JPTYO", "DEHAM",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 9, 1, 18, 0))));

        List<RouteCandidate> candidates = service.calculate("B-002");

        assertThat(candidates).isEmpty();
    }

    @Test
    void 経路設計依頼が存在しない場合は例外を投げる() {
        when(requestQueryService.findByBookingId("UNKNOWN")).thenReturn(null);

        assertThatThrownBy(() -> service.calculate("UNKNOWN"))
                .isInstanceOf(RouteDesignRequestNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }
}
