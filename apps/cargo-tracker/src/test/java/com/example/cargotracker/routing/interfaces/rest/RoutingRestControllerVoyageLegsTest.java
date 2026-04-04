package com.example.cargotracker.routing.interfaces.rest;

import com.example.cargotracker.routing.application.internal.queryservices.RouteDesignConditionQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageLegsQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageScheduleSearchService;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageLegDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoutingRestController.class)
@WithMockUser
@DisplayName("RoutingRestController - voyage legs エンドポイント")
class RoutingRestControllerVoyageLegsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteDesignConditionQueryService routeDesignConditionQueryService;

    @MockitoBean
    private VoyageScheduleSearchService voyageScheduleSearchService;

    @MockitoBean
    private VoyageLegsQueryService voyageLegsQueryService;

    @Test
    @DisplayName("voyageNumber を指定すると区間詳細リストを返す")
    void getLegsReturnsLegDetails() throws Exception {
        List<VoyageLegDetailResponse> legs = List.of(
                new VoyageLegDetailResponse("JPTYO", "SGSIN",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 0),
                new VoyageLegDetailResponse("SGSIN", "USNYC",
                        LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 15), 1)
        );
        when(voyageLegsQueryService.findByVoyageNumber("VOY-001")).thenReturn(legs);

        mockMvc.perform(get("/api/v1/routings/voyages/VOY-001/legs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].originLocode").value("JPTYO"))
                .andExpect(jsonPath("$[0].destinationLocode").value("SGSIN"))
                .andExpect(jsonPath("$[0].legOrder").value(0))
                .andExpect(jsonPath("$[1].originLocode").value("SGSIN"))
                .andExpect(jsonPath("$[1].legOrder").value(1));
    }

    @Test
    @DisplayName("存在しない voyageNumber は空リストを返す")
    void getLegsReturnsEmptyForUnknownVoyage() throws Exception {
        when(voyageLegsQueryService.findByVoyageNumber("UNKNOWN")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/routings/voyages/UNKNOWN/legs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
