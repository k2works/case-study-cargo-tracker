package com.example.cargotracker.billing.interfaces.rest;

import com.example.cargotracker.billing.application.internal.commandservices.InvoiceCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService;
import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService.InvoiceSummary;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceRestController.class)
@WithMockUser
@DisplayName("InvoiceRestController")
class InvoiceRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceCommandService invoiceCommandService;

    @MockitoBean
    private InvoiceQueryService invoiceQueryService;

    @Test
    @DisplayName("精算書を発行すると 201 Created を返す")
    void 精算書を発行すると201Createdを返す() throws Exception {
        InvoiceId invoiceId = InvoiceId.generate();
        InvoiceSummary summary = anySummary(invoiceId);

        when(invoiceCommandService.generateInvoice(any())).thenReturn(invoiceId);
        when(invoiceQueryService.findById(invoiceId.value().toString())).thenReturn(Optional.of(summary));

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": "booking-001", "freightChargeId": "charge-001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/invoices/")))
                .andExpect(jsonPath("$.id").value(invoiceId.value().toString()))
                .andExpect(jsonPath("$.bookingId").value("booking-001"))
                .andExpect(jsonPath("$.paymentStatus").value("支払い待ち"));
    }

    @Test
    @DisplayName("bookingId が空の場合 400 BadRequest を返す")
    void bookingIdが空の場合400BadRequestを返す() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": "", "freightChargeId": "charge-001"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("支払いを確認すると 200 OK を返す")
    void 支払いを確認すると200OKを返す() throws Exception {
        InvoiceId invoiceId = InvoiceId.generate();
        InvoiceSummary summary = new InvoiceSummary(
                invoiceId.value().toString(),
                "booking-001",
                "charge-001",
                new BigDecimal("10000"),
                LocalDate.now().plusDays(30),
                "支払い済み"
        );

        when(invoiceQueryService.findById(invoiceId.value().toString())).thenReturn(Optional.of(summary));

        mockMvc.perform(put("/api/v1/invoices/" + invoiceId.value() + "/confirm-payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invoiceId.value().toString()))
                .andExpect(jsonPath("$.paymentStatus").value("支払い済み"));
    }

    @Test
    @DisplayName("精算一覧を取得すると 200 OK を返す")
    void 精算一覧を取得すると200OKを返す() throws Exception {
        InvoiceId invoiceId = InvoiceId.generate();
        when(invoiceQueryService.findAll()).thenReturn(List.of(anySummary(invoiceId)));

        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(invoiceId.value().toString()));
    }

    @Test
    @DisplayName("存在しない精算書を取得すると 404 を返す")
    void 存在しない精算書を取得すると404を返す() throws Exception {
        when(invoiceQueryService.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/invoices/non-existent-id"))
                .andExpect(status().isNotFound());
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
}
