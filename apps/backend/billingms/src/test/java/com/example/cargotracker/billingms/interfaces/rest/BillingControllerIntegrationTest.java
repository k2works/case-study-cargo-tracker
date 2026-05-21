package com.example.cargotracker.billingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.billingms.infrastructure.persistence.InvoiceMapper;
import com.example.cargotracker.billingms.infrastructure.persistence.InvoiceRecord;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * BillingController 統合テスト（US21）。
 *
 * <p>CommandGateway はモックし、InvoiceMapper に直接シードデータを入れることで
 * 請求一覧・詳細・料金算出 API の振る舞いを検証する。</p>
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("BillingController 統合テスト")
class BillingControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommandGateway commandGateway;

    @Autowired
    private InvoiceMapper invoiceMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(commandGateway.send(any(), eq(Object.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private void seedInvoice(String invoiceId, String billingStatus) {
        var invoiceRecord = new InvoiceRecord(
                invoiceId,
                "B-2026-" + invoiceId,
                "SHP-001",
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                "JPY",
                billingStatus,
                null, null, null, null, null, null, 0L);
        invoiceMapper.insert(invoiceRecord);
    }

    @Test
    @DisplayName("GET /api/v1/billing/invoices で全請求一覧が返る")
    void listInvoices_全件取得() throws Exception {
        seedInvoice("INV-TEST-001", "CALCULATED");
        seedInvoice("INV-TEST-002", "PENDING");

        mockMvc.perform(get("/api/v1/billing/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("GET /api/v1/billing/invoices/{id} で請求詳細が返る")
    void getInvoice_詳細取得() throws Exception {
        seedInvoice("INV-TEST-003", "CALCULATED");

        mockMvc.perform(get("/api/v1/billing/invoices/{id}", "INV-TEST-003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value("INV-TEST-003"))
                .andExpect(jsonPath("$.billingStatus").value("CALCULATED"))
                .andExpect(jsonPath("$.currency").value("JPY"));
    }

    @Test
    @DisplayName("GET /api/v1/billing/invoices/{id} で存在しない場合は 404 を返す")
    void getInvoice_存在しない場合404() throws Exception {
        mockMvc.perform(get("/api/v1/billing/invoices/{id}", "INV-NOTEXIST-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/billing/invoices/{id}/calculate で CommandGateway に CalculateChargeCommand が送信される")
    void calculateCharge_CommandGatewayへ送信() throws Exception {
        seedInvoice("INV-TEST-004", "PENDING");

        mockMvc.perform(post("/api/v1/billing/invoices/{id}/calculate", "INV-TEST-004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "baseAmount": 115000,
                                    "currencyCode": "JPY",
                                    "discountRate": 0,
                                    "operatorId": "admin-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value("INV-TEST-004"))
                .andExpect(jsonPath("$.status").value("CALCULATED"));
    }

    @Test
    @DisplayName("POST /calculate で存在しない invoiceId の場合は 404 を返す")
    void calculateCharge_存在しない場合404() throws Exception {
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/calculate", "INV-NOTEXIST-999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "baseAmount": 100000,
                                    "currencyCode": "JPY",
                                    "discountRate": 0
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/billing/invoices/{id}/issue で IssueInvoiceCommand が送信される")
    void issueInvoice_CommandGatewayへ送信() throws Exception {
        seedInvoice("INV-TEST-005", "CALCULATED");

        mockMvc.perform(post("/api/v1/billing/invoices/{id}/issue", "INV-TEST-005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "paymentDue": "2026-09-30",
                                    "operatorId": "admin-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value("INV-TEST-005"))
                .andExpect(jsonPath("$.status").value("INVOICED"));
    }

    @Test
    @DisplayName("POST /issue で存在しない invoiceId の場合は 404 を返す")
    void issueInvoice_存在しない場合404() throws Exception {
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/issue", "INV-NOTEXIST-999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "paymentDue": "2026-09-30",
                                    "operatorId": "admin-001"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/v1/billing/invoices/{id}/settle で RecordPaymentCommand が送信される")
    void recordPayment_CommandGatewayへ送信() throws Exception {
        seedInvoice("INV-TEST-006", "INVOICED");

        mockMvc.perform(patch("/api/v1/billing/invoices/{id}/settle", "INV-TEST-006")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "paidAmount": 100000,
                                    "paymentMethod": "BANK_TRANSFER",
                                    "operatorId": "admin-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value("INV-TEST-006"))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("PATCH /settle で存在しない invoiceId の場合は 404 を返す")
    void recordPayment_存在しない場合404() throws Exception {
        mockMvc.perform(patch("/api/v1/billing/invoices/{id}/settle", "INV-NOTEXIST-999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "paidAmount": 100000,
                                    "paymentMethod": "BANK_TRANSFER"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/billing/invoices/overdue で督促一覧が返る")
    void listOverdueInvoices_正常取得() throws Exception {
        mockMvc.perform(get("/api/v1/billing/invoices/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("calculate で CommandGateway が IllegalArgumentException を返した場合 400 Bad Request")
    void calculateCharge_IllegalArgumentExceptionで400() throws Exception {
        seedInvoice("INV-TEST-007", "PENDING");
        when(commandGateway.send(any(), eq(Object.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalArgumentException("割引率は 30% 以下")));

        mockMvc.perform(post("/api/v1/billing/invoices/{id}/calculate", "INV-TEST-007")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "baseAmount": 100000,
                                    "currencyCode": "JPY",
                                    "discountRate": 0.31
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("calculate で CommandGateway が IllegalStateException を返した場合 409 Conflict")
    void calculateCharge_IllegalStateExceptionで409() throws Exception {
        seedInvoice("INV-TEST-008", "CALCULATED");
        when(commandGateway.send(any(), eq(Object.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException("PENDING 状態でのみ実行可能")));

        mockMvc.perform(post("/api/v1/billing/invoices/{id}/calculate", "INV-TEST-008")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "baseAmount": 100000,
                                    "currencyCode": "JPY",
                                    "discountRate": 0
                                }
                                """))
                .andExpect(status().isConflict());
    }
}
