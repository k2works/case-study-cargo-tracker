package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.application.internal.outboundservices.BookingSnapshot;
import com.example.cargotracker.routing.application.internal.queryservices.BookingDataNotFoundException;
import com.example.cargotracker.routing.application.internal.queryservices.RouteDesignConditionQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService;
import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
import com.example.cargotracker.routing.domain.model.RouteDesignCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(RoutingWebController.class)
@WithMockUser
@DisplayName("RoutingWebController")
class RoutingWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteSearchService routeSearchService;

    @MockitoBean
    private BookingQueryPort bookingQueryPort;

    @MockitoBean
    private RouteDesignConditionQueryService routeDesignConditionQueryService;

    // ── GET /routings/search?bookingId={id} ───────────────────────────────

    @Test
    @DisplayName("予約 ID 指定でルート候補一覧を表示できる")
    void search_bookingId_候補あり() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingSnapshot snapshot = anySnapshot();
        when(bookingQueryPort.findById(bookingId)).thenReturn(Optional.of(snapshot));
        when(routeSearchService.searchByCondition(any())).thenReturn(List.of(anyCandidate()));

        mockMvc.perform(get("/routings/search").param("bookingId", bookingId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("routing/search"))
                .andExpect(model().attributeExists("candidates"))
                .andExpect(model().attributeExists("form"))
                .andExpect(model().attribute("bookingId", bookingId));
    }

    @Test
    @DisplayName("候補がゼロ件の場合も routing/search を表示する")
    void search_bookingId_候補なし() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingQueryPort.findById(bookingId)).thenReturn(Optional.of(anySnapshot()));
        when(routeSearchService.searchByCondition(any())).thenReturn(List.of());

        mockMvc.perform(get("/routings/search").param("bookingId", bookingId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("routing/search"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    @DisplayName("存在しない予約 ID を指定した場合は 404 を返す")
    void search_bookingId_存在しない() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingQueryPort.findById(bookingId))
                .thenThrow(new BookingDataNotFoundException(bookingId));

        mockMvc.perform(get("/routings/search").param("bookingId", bookingId.toString()))
                .andExpect(status().isNotFound());
    }

    // ── GET /routings/search?originLocode=...（直接条件指定） ─────────────────

    @Test
    @DisplayName("直接条件指定でルート候補一覧を表示できる")
    void search_condition_候補あり() throws Exception {
        when(routeSearchService.searchByCondition(any())).thenReturn(List.of(anyCandidate()));

        mockMvc.perform(get("/routings/search")
                        .param("originLocode", "JPTYO")
                        .param("destinationLocode", "SGSIN")
                        .param("requestedArrivalDate", "2026-06-01")
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("routing/search"))
                .andExpect(model().attributeExists("candidates"))
                .andExpect(model().attributeExists("form"));
    }

    // ── GET /routings/search（パラメータなし）──────────────────────────────

    @Test
    @DisplayName("パラメータなしでアクセスした場合は予約一覧へリダイレクトする")
    void search_パラメータなし() throws Exception {
        mockMvc.perform(get("/routings/search"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings"));
    }

    // ── GET /routings/design-condition?bookingId={id} ────────────────────

    @Test
    @DisplayName("予約 ID 指定で経路設計条件画面を表示できる")
    void designCondition_正常系() throws Exception {
        UUID bookingId = UUID.randomUUID();
        var condition = new RouteDesignCondition(
            bookingId, "JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 30),
            CargoType.GENERAL,
            new BigDecimal("500.0")
        );
        when(routeDesignConditionQueryService.findByBookingId(bookingId)).thenReturn(condition);

        mockMvc.perform(get("/routings/design-condition").param("bookingId", bookingId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("routing/design-condition"))
                .andExpect(model().attributeExists("condition"))
                .andExpect(model().attribute("bookingId", bookingId));
    }

    @Test
    @DisplayName("存在しない予約 ID の場合は 404 を返す")
    void designCondition_予約なし() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(routeDesignConditionQueryService.findByBookingId(bookingId))
            .thenThrow(new BookingDataNotFoundException(bookingId));

        mockMvc.perform(get("/routings/design-condition").param("bookingId", bookingId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("bookingId パラメータなしは 400 を返す")
    void designCondition_パラメータなし() throws Exception {
        mockMvc.perform(get("/routings/design-condition"))
                .andExpect(status().isBadRequest());
    }

    // ── ヘルパー ─────────────────────────────────────────────────────────────

    private BookingSnapshot anySnapshot() {
        return new BookingSnapshot(
                "JPTYO", "SGSIN",
                LocalDate.of(2026, 6, 1),
                CargoType.GENERAL,
                new BigDecimal("1000")
        );
    }

    private RouteCandidate anyCandidate() {
        return new RouteCandidate(
                "SG001",
                List.of(),
                14,
                new BigDecimal("150000"),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 5, 14),
                Set.of(CargoType.GENERAL)
        );
    }
}
