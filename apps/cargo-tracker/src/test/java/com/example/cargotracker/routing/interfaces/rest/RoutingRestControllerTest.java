package com.example.cargotracker.routing.interfaces.rest;

import com.example.cargotracker.routing.application.internal.queryservices.BookingDataNotFoundException;
import com.example.cargotracker.routing.application.internal.queryservices.RouteDesignConditionQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageLegsQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageScheduleSearchService;
import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoutingRestController.class)
@WithMockUser
@DisplayName("RoutingRestController")
class RoutingRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteSearchService routeSearchService;

    @MockitoBean
    private RouteDesignConditionQueryService routeDesignConditionQueryService;

    @MockitoBean
    private VoyageScheduleSearchService voyageScheduleSearchService;

    @MockitoBean
    private VoyageLegsQueryService voyageLegsQueryService;

    @Test
    @DisplayName("予約 ID でルート候補リストを JSON で取得できる")
    void search_正常系() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(routeSearchService.searchByBookingId(bookingId)).thenReturn(List.of(anyCandidate()));

        mockMvc.perform(get("/api/v1/routings/search").param("bookingId", bookingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].voyageNumber").value("SG001"))
                .andExpect(jsonPath("$[0].transitDays").value(14))
                .andExpect(jsonPath("$[0].estimatedPrice").value(150000))
                .andExpect(jsonPath("$[0].estimatedArrival").value("2026-05-28"))
                .andExpect(jsonPath("$[0].supportedCargoTypes[0]").value("GENERAL"));
    }

    @Test
    @DisplayName("候補がない場合は空配列を返す")
    void search_候補なし() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(routeSearchService.searchByBookingId(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/routings/search").param("bookingId", bookingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("存在しない予約 ID は 404 を返す")
    void search_予約なし() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(routeSearchService.searchByBookingId(bookingId))
                .thenThrow(new BookingDataNotFoundException(bookingId));

        mockMvc.perform(get("/api/v1/routings/search").param("bookingId", bookingId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(
                        "ルート検索に必要な予約データが見つかりません: " + bookingId));
    }

    @Test
    @DisplayName("bookingId パラメータなしは 400 を返す")
    void search_パラメータなし() throws Exception {
        mockMvc.perform(get("/api/v1/routings/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ルート検索サービスが利用できない場合は 503 を返す")
    void search_サービス利用不可() throws Exception {
        RoutingRestController controller = new RoutingRestController(
            java.util.Optional.empty(),
            routeDesignConditionQueryService,
            voyageScheduleSearchService,
            voyageLegsQueryService);

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/routings/search").param("bookingId", UUID.randomUUID().toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("ルート検索サービスは現在利用できません"));
    }

    private RouteCandidate anyCandidate() {
        return new RouteCandidate(
                "SG001",
                List.of("SGSIN"),
                14,
                new BigDecimal("150000"),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 5, 14),
                Set.of(CargoType.GENERAL)
        );
    }
}
