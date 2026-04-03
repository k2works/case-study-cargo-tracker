package com.example.cargotracker.billing.interfaces.web;

import com.example.cargotracker.billing.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.billing.application.internal.commandservices.CalculateFreightCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService.FreightChargeSummary;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(FreightWebController.class)
@WithMockUser
@DisplayName("FreightWebController")
class FreightWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalculateFreightCommandService calculateFreightCommandService;

    @MockitoBean
    private FreightChargeQueryService freightChargeQueryService;

    @Test
    @DisplayName("GET /freight — 一覧ページを表示する")
    void list() throws Exception {
        when(freightChargeQueryService.findAll()).thenReturn(List.of(anySummary()));

        mockMvc.perform(get("/freight"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/list"))
                .andExpect(model().attributeExists("freightCharges"));
    }

    @Test
    @DisplayName("GET /freight/calculate — 算出フォームを表示する")
    void showCalculateForm() throws Exception {
        mockMvc.perform(get("/freight/calculate"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/calculate"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    @DisplayName("POST /freight/calculate バリデーション OK — /freight にリダイレクト")
    void calculateSuccess() throws Exception {
        when(calculateFreightCommandService.calculate(any())).thenReturn(FreightId.generate());

        mockMvc.perform(post("/freight/calculate")
                        .param("bookingId", "booking-001")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/freight"));

        verify(calculateFreightCommandService).calculate(any());
    }

    @Test
    @DisplayName("POST /freight/calculate バリデーション NG — 算出フォームに戻る")
    void calculateValidationError() throws Exception {
        mockMvc.perform(post("/freight/calculate")
                        .param("bookingId", "")  // 空文字でバリデーションエラー
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/calculate"))
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    @DisplayName("POST /freight/calculate BookingNotFoundException — errorMessage 付きで /freight にリダイレクト")
    void calculateBookingNotFound() throws Exception {
        when(calculateFreightCommandService.calculate(any()))
                .thenThrow(new BookingNotFoundException("booking-999"));

        mockMvc.perform(post("/freight/calculate")
                        .param("bookingId", "booking-999")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/freight"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("POST /freight/{id}/confirm — /freight にリダイレクト")
    void confirm() throws Exception {
        FreightId freightId = FreightId.generate();

        mockMvc.perform(post("/freight/" + freightId.value() + "/confirm")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/freight"));

        verify(calculateFreightCommandService).confirm(any());
    }

    private FreightChargeSummary anySummary() {
        return new FreightChargeSummary(
                FreightId.generate().value().toString(),
                "booking-001",
                "算出中",
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                new BigDecimal("10000")
        );
    }
}
