package com.example.cargotracker.bookingms.interfaces.rest;

import com.example.cargotracker.bookingms.domain.model.commands.CreateQuotationCommand;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationCandidateRecord;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationMapper;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationRecord;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QuotationController 統合テスト（US01）。CommandGateway は MockitoBean で置き換える。
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("QuotationController 統合テスト")
class QuotationControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommandGateway commandGateway;

    @Autowired
    private QuotationMapper quotationMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(commandGateway.sendAndWait(any())).thenReturn(null);
    }

    private Long registerShipper() throws Exception {
        var result = mockMvc.perform(post("/api/v1/shippers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "テスト荷主",
                                    "email": "quotation-test-%d@example.com",
                                    "phone": "090-0000-0000",
                                    "shipperType": "INDIVIDUAL"
                                }
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    @Test
    @DisplayName("US01: POST /api/v1/quotations で見積を作成できる（201、CommandGateway に CreateQuotationCommand 送信）")
    void 見積作成できる() throws Exception {
        Long shipperId = registerShipper();
        String json = """
                {
                    "shipperId": %d,
                    "originUnLocode": "JPTYO",
                    "destinationUnLocode": "USNYC",
                    "arrivalDeadline": "%s",
                    "cargoType": "GENERAL",
                    "weightKg": 100
                }
                """.formatted(shipperId, LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/v1/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quotationId").exists())
                .andExpect(jsonPath("$.status").value("CREATED"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(CreateQuotationCommand.class);
        CreateQuotationCommand cmd = (CreateQuotationCommand) captor.getValue();
        assertThat(cmd.shipperId().value()).isEqualTo(shipperId);
        assertThat(cmd.cargoType().name()).isEqualTo("GENERAL");
        assertThat(cmd.weightKg()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("US01: 存在しない shipperId は 400 を返す")
    void 存在しない荷主は400() throws Exception {
        String json = """
                {
                    "shipperId": 9999999,
                    "originUnLocode": "JPTYO",
                    "destinationUnLocode": "USNYC",
                    "arrivalDeadline": "%s",
                    "cargoType": "GENERAL",
                    "weightKg": 100
                }
                """.formatted(LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/v1/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("US01: HAZARDOUS で hazardInfo が無いと 400 を返す（受入条件 6）")
    void 危険物で申告無しは400() throws Exception {
        Long shipperId = registerShipper();
        String json = """
                {
                    "shipperId": %d,
                    "originUnLocode": "JPTYO",
                    "destinationUnLocode": "USNYC",
                    "arrivalDeadline": "%s",
                    "cargoType": "HAZARDOUS",
                    "weightKg": 100
                }
                """.formatted(shipperId, LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/v1/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("US01: GET /api/v1/quotations/{id} で Read Model から見積詳細と候補を返す")
    void 見積詳細を取得できる() throws Exception {
        // Read Model 直接投入（@MockitoBean CommandGateway のため Aggregate / Event は走らない）
        var q = new QuotationRecord();
        q.setQuotationId("Q-IT-001");
        q.setShipperId(1L);
        q.setOriginUnlocode("JPTYO");
        q.setDestinationUnlocode("USNYC");
        q.setArrivalDeadline(LocalDate.of(2026, 12, 31));
        q.setCargoType("GENERAL");
        q.setWeightKg(new BigDecimal("100"));
        q.setEstimatedAmount(new BigDecimal("100000.00"));
        q.setEstimatedCurrency("JPY");
        q.setValidUntil(LocalDate.of(2026, 12, 31));
        q.setStatus("OFFERED");
        quotationMapper.insertQuotation(q);

        var c = new QuotationCandidateRecord();
        c.setQuotationId("Q-IT-001");
        c.setCandidateSeq(1);
        c.setEstimatedDays(14);
        c.setEstimatedCost(new BigDecimal("100000.00"));
        c.setEstimatedCurrency("JPY");
        c.setItinerarySummary("JPTYO → USNYC");
        c.setVoyageNumbers("TBD");
        quotationMapper.insertCandidate(c);

        mockMvc.perform(get("/api/v1/quotations/Q-IT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotationId").value("Q-IT-001"))
                .andExpect(jsonPath("$.status").value("OFFERED"))
                .andExpect(jsonPath("$.estimatedAmount").value(100000.00))
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.candidates[0].candidateSeq").value(1))
                .andExpect(jsonPath("$.candidates[0].estimatedDays").value(14))
                .andExpect(jsonPath("$.candidates[0].itinerarySummary").value("JPTYO → USNYC"));
    }

    @Test
    @DisplayName("US01: GET で存在しない quotation_id は 404 を返す")
    void 存在しない見積は404() throws Exception {
        mockMvc.perform(get("/api/v1/quotations/NOTFOUND"))
                .andExpect(status().isNotFound());
    }
}
