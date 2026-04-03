package com.example.cargotracker.billing.interfaces.web;

import com.example.cargotracker.billing.application.internal.commandservices.InvoiceCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService.FreightChargeSummary;
import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService;
import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService.InvoiceSummary;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

@WebMvcTest(InvoiceWebController.class)
@WithMockUser
@DisplayName("InvoiceWebController")
class InvoiceWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceCommandService invoiceCommandService;

    @MockitoBean
    private InvoiceQueryService invoiceQueryService;

    @MockitoBean
    private FreightChargeQueryService freightChargeQueryService;

    @Test
    @DisplayName("GET /invoices — 精算一覧ページを表示する")
    void list() throws Exception {
        InvoiceId invoiceId = InvoiceId.generate();
        when(invoiceQueryService.findAll()).thenReturn(List.of(anySummary(invoiceId)));

        mockMvc.perform(get("/invoices"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/invoices"))
                .andExpect(model().attributeExists("invoices"));
    }

    @Test
    @DisplayName("GET /invoices/{id} — 精算書詳細ページを表示する")
    void detail() throws Exception {
        InvoiceId invoiceId = InvoiceId.generate();
        when(invoiceQueryService.findById(invoiceId.value().toString()))
                .thenReturn(Optional.of(anySummary(invoiceId)));
        when(freightChargeQueryService.findById("charge-001"))
                .thenReturn(Optional.of(anyFreightChargeSummary()));

        mockMvc.perform(get("/invoices/" + invoiceId.value()))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/invoice-detail"))
                .andExpect(model().attributeExists("invoice"));
    }

    @Test
    @DisplayName("GET /invoices/{id} 存在しない場合 — /invoices にリダイレクト")
    void detailNotFound() throws Exception {
        when(invoiceQueryService.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/invoices/non-existent"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices"));
    }

    @Test
    @DisplayName("POST /invoices 正常 — successMessage 付きで /invoices にリダイレクト")
    void generateInvoiceSuccess() throws Exception {
        InvoiceId invoiceId = InvoiceId.generate();
        when(invoiceCommandService.generateInvoice(any())).thenReturn(invoiceId);

                mockMvc.perform(post("/invoices")
                        .param("bookingId", "booking-001")
                        .param("freightChargeId", "charge-001")
                        .param("dueDate", "2026-05-31")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices"))
                .andExpect(flash().attribute("successMessage", "精算書を発行し、荷主へメール通知しました"));

        verify(invoiceCommandService).generateInvoice(any());
    }

    @Test
    @DisplayName("POST /invoices 例外発生 — errorMessage 付きで /invoices にリダイレクト")
    void generateInvoiceError() throws Exception {
        doThrow(new IllegalStateException("輸送料金が確定されていません"))
                .when(invoiceCommandService).generateInvoice(any());

        mockMvc.perform(post("/invoices")
                        .param("bookingId", "booking-001")
                        .param("freightChargeId", "charge-001")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("POST /invoices/{id}/confirm-payment 正常 — successMessage 付きで /invoices にリダイレクト")
    void confirmPaymentSuccess() throws Exception {
        InvoiceId invoiceId = InvoiceId.generate();

        mockMvc.perform(post("/invoices/" + invoiceId.value() + "/confirm-payment")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices"))
                .andExpect(flash().attribute("successMessage", "決済機関との連携により入金を確認し、精算を完了しました"));

        verify(invoiceCommandService).confirmPayment(any());
    }

    @Test
    @DisplayName("POST /invoices/{id}/confirm-payment 例外発生 — errorMessage 付きで /invoices にリダイレクト")
    void confirmPaymentError() throws Exception {
        InvoiceId invoiceId = InvoiceId.generate();
        doThrow(new IllegalStateException("支払い待ち状態の精算書のみ支払い確認できます"))
                .when(invoiceCommandService).confirmPayment(any());

        mockMvc.perform(post("/invoices/" + invoiceId.value() + "/confirm-payment")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    private InvoiceSummary anySummary(InvoiceId invoiceId) {
        return new InvoiceSummary(
                invoiceId.value().toString(),
                "booking-001",
                "charge-001",
                new BigDecimal("10000"),
                LocalDate.now().plusDays(30),
                "支払い待ち"
        );
    }

    private FreightChargeSummary anyFreightChargeSummary() {
        return new FreightChargeSummary(
                "charge-001",
                "booking-001",
                "確定",
                new BigDecimal("10000"),
                new BigDecimal("-1000"),
                new BigDecimal("9000"),
                new BigDecimal("10")
        );
    }
}
