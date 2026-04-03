package com.example.cargotracker.billing.interfaces.rest;

import com.example.cargotracker.billing.application.internal.commandservices.ApplyDiscountCommandService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private ApplyDiscountCommandService applyDiscountCommandService;

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

    @Test
    @DisplayName("PUT /api/v1/freight-charges/{id}/apply-discount 正常 — 200 と更新後の FreightChargeResponse を返す")
    void applyDiscountSuccess() throws Exception {
        FreightId freightId = FreightId.generate();
        FreightChargeSummary summary = new FreightChargeSummary(
                freightId.value().toString(),
                "booking-001",
                "算出中",
                new BigDecimal("10000"),
                new BigDecimal("-1000"),
                new BigDecimal("9000")
        );

        when(freightChargeQueryService.findById(freightId.value().toString()))
                .thenReturn(Optional.of(summary));

        mockMvc.perform(put("/api/v1/freight-charges/" + freightId.value() + "/apply-discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": "booking-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(freightId.value().toString()))
                .andExpect(jsonPath("$.adjustmentAmount").value(-1000))
                .andExpect(jsonPath("$.totalAmount").value(9000));
    }

    @Test
    @DisplayName("PUT /api/v1/freight-charges/{id}/apply-discount bookingId が空 — 400 BadRequest を返す")
    void applyDiscountBadRequest_emptyBookingId() throws Exception {
        FreightId freightId = FreightId.generate();

        mockMvc.perform(put("/api/v1/freight-charges/" + freightId.value() + "/apply-discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/freight-charges/{id}/apply-discount IllegalStateException — 409 Conflict を返す")
    void applyDiscount_IllegalStateException_returns409() throws Exception {
        FreightId freightId = FreightId.generate();
        doThrow(new IllegalStateException("割引を適用できない状態です"))
                .when(applyDiscountCommandService).applyDiscount(any());

        mockMvc.perform(put("/api/v1/freight-charges/" + freightId.value() + "/apply-discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": "booking-001"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /api/v1/freight-charges/{id}/apply-discount IllegalArgumentException — 404 Not Found を返す")
    void applyDiscount_IllegalArgumentException_returns404() throws Exception {
        FreightId freightId = FreightId.generate();
        doThrow(new IllegalArgumentException("輸送料金が見つかりません"))
                .when(applyDiscountCommandService).applyDiscount(any());

        mockMvc.perform(put("/api/v1/freight-charges/" + freightId.value() + "/apply-discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": "booking-001"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/freight-charges/{id}/confirm IllegalStateException — 409 Conflict を返す")
    void confirm_IllegalStateException_returns409() throws Exception {
        FreightId freightId = FreightId.generate();
        doThrow(new IllegalStateException("確定できない状態です"))
                .when(calculateFreightCommandService).confirm(any());

        mockMvc.perform(post("/api/v1/freight-charges/" + freightId.value() + "/confirm"))
                .andExpect(status().isConflict());
    }
}
