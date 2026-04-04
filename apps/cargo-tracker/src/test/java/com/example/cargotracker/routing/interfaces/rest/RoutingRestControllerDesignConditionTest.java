package com.example.cargotracker.routing.interfaces.rest;

import com.example.cargotracker.routing.application.internal.queryservices.BookingDataNotFoundException;
import com.example.cargotracker.routing.application.internal.queryservices.RouteDesignConditionQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageScheduleSearchService;
import com.example.cargotracker.routing.domain.model.CargoType;
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
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoutingRestController.class)
@WithMockUser
@DisplayName("RoutingRestController - design-condition エンドポイント")
class RoutingRestControllerDesignConditionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteDesignConditionQueryService routeDesignConditionQueryService;

    @MockitoBean
    private VoyageScheduleSearchService voyageScheduleSearchService;

    @Test
    @DisplayName("bookingId に対応する経路設計条件を JSON で返す")
    void designCondition_正常系() throws Exception {
        var bookingId = UUID.randomUUID();
        var condition = new RouteDesignCondition(
            bookingId, "JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 30),
            CargoType.GENERAL,
            new BigDecimal("500.0")
        );
        when(routeDesignConditionQueryService.findByBookingId(bookingId)).thenReturn(condition);

        mockMvc.perform(get("/api/v1/routings/design-condition")
                .param("bookingId", bookingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.originLocode").value("JPTYO"))
                .andExpect(jsonPath("$.destinationLocode").value("SGSIN"))
                .andExpect(jsonPath("$.requestedArrivalDate").value("2026-06-30"))
                .andExpect(jsonPath("$.cargoType").value("GENERAL"))
                .andExpect(jsonPath("$.weightKg").value(500.0))
                .andExpect(jsonPath("$.complete").value(true));
    }

    @Test
    @DisplayName("存在しない予約 ID は 404 を返す")
    void designCondition_予約なし() throws Exception {
        var bookingId = UUID.randomUUID();
        when(routeDesignConditionQueryService.findByBookingId(bookingId))
            .thenThrow(new BookingDataNotFoundException(bookingId));

        mockMvc.perform(get("/api/v1/routings/design-condition")
                .param("bookingId", bookingId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(
                    "ルート検索に必要な予約データが見つかりません: " + bookingId));
    }

    @Test
    @DisplayName("bookingId パラメータなしは 400 を返す")
    void designCondition_パラメータなし() throws Exception {
        mockMvc.perform(get("/api/v1/routings/design-condition"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("条件が不完全な予約でも 200 で complete=false を返す")
    void designCondition_条件不完全() throws Exception {
        var bookingId = UUID.randomUUID();
        var condition = new RouteDesignCondition(
            bookingId, null, "SGSIN", null, null, null
        );
        when(routeDesignConditionQueryService.findByBookingId(bookingId)).thenReturn(condition);

        mockMvc.perform(get("/api/v1/routings/design-condition")
                .param("bookingId", bookingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false));
    }
}
