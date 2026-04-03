package com.example.cargotracker.tracking.interfaces.rest;

import com.example.cargotracker.tracking.application.internal.queryservices.TrackingInfoDto;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrackingRestController.class)
class TrackingRestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TrackingQueryService trackingQueryService;

    @Test
    @DisplayName("有効な追跡番号で 200 OK と荷役履歴が返る")
    @WithMockUser
    void getByValidTrackingNumber() throws Exception {
        UUID bookingId = UUID.randomUUID();
        TrackingInfoDto dto = new TrackingInfoDto(
                "TRK-ABC12345",
                bookingId,
                "JPTYO",
                "SGSIN",
                LocalDate.of(2026, 6, 1),
                "積み込み",
                "JPTYO",
                List.of(new TrackingInfoDto.HandlingEventSummary(
                        LocalDateTime.of(2026, 5, 1, 9, 0),
                        "JPTYO",
                        "LOAD",
                        "積み込み",
                        null
                )),
                List.of()
        );
        when(trackingQueryService.findTrackingInfo("TRK-ABC12345"))
                .thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/v1/tracking/TRK-ABC12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRK-ABC12345"))
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.handlingHistory").isArray())
                .andExpect(jsonPath("$.handlingHistory[0].eventType").value("LOAD"))
                .andExpect(jsonPath("$.handlingHistory[0].eventTypeDisplayName").value("積み込み"))
                .andExpect(jsonPath("$.handlingHistory[0].locationCode").value("JPTYO"));
    }

    @Test
    @DisplayName("荷役履歴なしの追跡番号でも 200 OK と空配列が返る")
    @WithMockUser
    void getByValidTrackingNumber_noHistory() throws Exception {
        UUID bookingId = UUID.randomUUID();
        TrackingInfoDto dto = new TrackingInfoDto("TRK-ABC12345", bookingId, "JPTYO", "SGSIN", null, "未受取", "JPTYO", List.of(), List.of());
        when(trackingQueryService.findTrackingInfo("TRK-ABC12345"))
                .thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/v1/tracking/TRK-ABC12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handlingHistory").isArray())
                .andExpect(jsonPath("$.handlingHistory").isEmpty());
    }

    @Test
    @DisplayName("存在しない追跡番号は 404 を返す")
    @WithMockUser
    void getByUnknownTrackingNumber() throws Exception {
        when(trackingQueryService.findTrackingInfo(anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tracking/TRK-UNKNOWN1"))
                .andExpect(status().isNotFound());
    }
}
