package com.example.cargotracker.tracking.interfaces.rest;

import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingEntry;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TrackingRestController.class)
class TrackingRestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TrackingQueryService trackingQueryService;

    @Test
    @DisplayName("有効な追跡番号で 200 OK と予約 ID が返る")
    @WithMockUser
    void getByValidTrackingNumber() throws Exception {
        UUID bookingId = UUID.randomUUID();
        TrackingEntry entry = new TrackingEntry(new TrackingNumber("TRK-ABC12345"), bookingId);
        when(trackingQueryService.findByTrackingNumber("TRK-ABC12345"))
                .thenReturn(Optional.of(entry));

        mockMvc.perform(get("/api/v1/tracking/TRK-ABC12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRK-ABC12345"))
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()));
    }

    @Test
    @DisplayName("存在しない追跡番号は 404 を返す")
    @WithMockUser
    void getByUnknownTrackingNumber() throws Exception {
        when(trackingQueryService.findByTrackingNumber(anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tracking/TRK-UNKNOWN1"))
                .andExpect(status().isNotFound());
    }
}
