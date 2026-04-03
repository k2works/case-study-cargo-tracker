package com.example.cargotracker.billing.interfaces.rest;

import com.example.cargotracker.billing.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.billing.application.internal.commandservices.CalculateFreightCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService.FreightChargeSummary;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FreightRestController.class)
@WithMockUser
@DisplayName("FreightRestController")
class FreightRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalculateFreightCommandService calculateFreightCommandService;

    @MockitoBean
    private FreightChargeQueryService freightChargeQueryService;

    @Test
    @DisplayName("POST /api/v1/freight-charges 正常 — 201 と FreightChargeResponse を返す")
    void calculateSuccess() throws Exception {
        FreightId freightId = FreightId.generate();
        FreightChargeSummary summary = anySummary(freightId);

        when(calculateFreightCommandService.calculate(any())).thenReturn(freightId);
        when(freightChargeQueryService.findById(freightId.value().toString()))
                .thenReturn(Optional.of(summary));

        mockMvc.perform(post("/api/v1/freight-charges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": "booking-001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(freightId.value().toString()))
                .andExpect(jsonPath("$.bookingId").value("booking-001"))
                .andExpect(jsonPath("$.status").value("算出中"));
    }

    @Test
    @DisplayName("POST /api/v1/freight-charges BookingNotFoundException — 404 を返す")
    void calculateBookingNotFound() throws Exception {
        when(calculateFreightCommandService.calculate(any()))
                .thenThrow(new BookingNotFoundException("booking-999"));

        mockMvc.perform(post("/api/v1/freight-charges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": "booking-999"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/freight-charges/{id} 正常 — 200 と FreightChargeResponse を返す")
    void findById() throws Exception {
        FreightId freightId = FreightId.generate();
        FreightChargeSummary summary = anySummary(freightId);

        when(freightChargeQueryService.findById(freightId.value().toString()))
                .thenReturn(Optional.of(summary));

        mockMvc.perform(get("/api/v1/freight-charges/" + freightId.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(freightId.value().toString()))
                .andExpect(jsonPath("$.bookingId").value("booking-001"));
    }

    private FreightChargeSummary anySummary(FreightId freightId) {
        return new FreightChargeSummary(
                freightId.value().toString(),
                "booking-001",
                "算出中",
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                new BigDecimal("10000")
        );
    }
}
