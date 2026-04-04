package com.example.cargotracker.routing.interfaces.rest;

import com.example.cargotracker.routing.application.internal.queryservices.VoyageScheduleSearchService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteDesignConditionQueryService;
import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageLeg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoutingRestController.class)
@WithMockUser
@DisplayName("RoutingRestController - voyage-schedules エンドポイント")
class RoutingRestControllerVoyageSchedulesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteDesignConditionQueryService routeDesignConditionQueryService;

    @MockitoBean
    private VoyageScheduleSearchService voyageScheduleSearchService;

    private final Voyage sg001 = new Voyage(
        "SG001", "Japan Pacific Lines",
        Set.of(CargoType.GENERAL, CargoType.REFRIGERATED),
        List.of(new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)))
    );

    @Test
    @DisplayName("origin/dest/deadline を指定して航海スケジュール一覧を返す")
    void voyageSchedules_正常系() throws Exception {
        when(voyageScheduleSearchService.search("JPTYO", "SGSIN", LocalDate.of(2026, 6, 20)))
            .thenReturn(List.of(sg001));

        mockMvc.perform(get("/api/v1/routings/voyage-schedules")
                .param("origin", "JPTYO")
                .param("dest", "SGSIN")
                .param("deadline", "2026-06-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].voyageNumber").value("SG001"))
            .andExpect(jsonPath("$[0].carrierName").value("Japan Pacific Lines"))
            .andExpect(jsonPath("$[0].legs[0].originLocode").value("JPTYO"))
            .andExpect(jsonPath("$[0].legs[0].destinationLocode").value("SGSIN"))
            .andExpect(jsonPath("$[0].legs[0].departureDate").value("2026-06-01"))
            .andExpect(jsonPath("$[0].legs[0].arrivalDate").value("2026-06-15"))
            .andExpect(jsonPath("$[0].legs[0].transitDays").value(14));
    }

    @Test
    @DisplayName("deadline なしで全航海を返す")
    void voyageSchedules_期限なし() throws Exception {
        when(voyageScheduleSearchService.search("JPTYO", "SGSIN", null))
            .thenReturn(List.of(sg001));

        mockMvc.perform(get("/api/v1/routings/voyage-schedules")
                .param("origin", "JPTYO")
                .param("dest", "SGSIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].voyageNumber").value("SG001"));
    }

    @Test
    @DisplayName("該当なしの場合は空配列を返す")
    void voyageSchedules_空() throws Exception {
        when(voyageScheduleSearchService.search("ZZZXX", "YYYYY", null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/routings/voyage-schedules")
                .param("origin", "ZZZXX")
                .param("dest", "YYYYY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("origin パラメータなしは 400 を返す")
    void voyageSchedules_パラメータなし() throws Exception {
        mockMvc.perform(get("/api/v1/routings/voyage-schedules")
                .param("dest", "SGSIN"))
            .andExpect(status().isBadRequest());
    }
}
