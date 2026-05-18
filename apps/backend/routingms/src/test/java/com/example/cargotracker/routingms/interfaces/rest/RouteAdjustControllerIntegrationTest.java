package com.example.cargotracker.routingms.interfaces.rest;

import com.example.cargotracker.routingms.infrastructure.persistence.CarrierMovementRecord;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageRecord;
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
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/routing/adjust 統合テスト（US10）。
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("RouteAdjustController 統合テスト")
class RouteAdjustControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommandGateway commandGateway;

    @Autowired
    private VoyageMapper voyageMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        VoyageRecord voyage = new VoyageRecord();
        voyage.setVoyageNumber("V-ADJ01");
        voyage.setCarrierCode("YMT");
        voyage.setCarrierName("Test Lines");
        voyage.setShipName("MV Adjust");
        voyage.setDepartureDate(LocalDateTime.of(2099, 8, 1, 9, 0));
        voyage.setArrivalDate(LocalDateTime.of(2099, 8, 15, 18, 0));
        voyage.setOriginUnlocode("JPYOK");
        voyage.setDestinationUnlocode("USLAX");
        voyage.setStatus("SCHEDULED");
        voyageMapper.insertVoyage(voyage);

        CarrierMovementRecord movement = new CarrierMovementRecord();
        movement.setVoyageNumber("V-ADJ01");
        movement.setMovementSeq(1);
        movement.setDepartureUnlocode("JPYOK");
        movement.setArrivalUnlocode("USLAX");
        movement.setDepartureTime(LocalDateTime.of(2099, 8, 1, 9, 0));
        movement.setArrivalTime(LocalDateTime.of(2099, 8, 15, 18, 0));
        voyageMapper.insertCarrierMovement(movement);
        voyageMapper.insertAcceptedCargoType("V-ADJ01", "GENERAL");
    }

    @Test
    void US10_条件調整_候補あり_再算出結果を返す() throws Exception {
        String body = """
                {
                  "origin": "JPYOK",
                  "destination": "USLAX",
                  "arrivalDeadline": "2099-12-31",
                  "cargoType": "GENERAL"
                }
                """;

        mockMvc.perform(post("/api/v1/routing/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.candidates[0].voyageNumbers[0]").value("V-ADJ01"))
                .andExpect(jsonPath("$.noRoutesMessage").doesNotExist());
    }

    @Test
    void US10_条件調整_候補ゼロ_営業担当者メッセージを返す() throws Exception {
        String body = """
                {
                  "origin": "JPYOK",
                  "destination": "USLAX",
                  "arrivalDeadline": "2000-01-01",
                  "cargoType": "GENERAL"
                }
                """;

        mockMvc.perform(post("/api/v1/routing/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.candidates").isEmpty())
                .andExpect(jsonPath("$.noRoutesMessage").isString());
    }
}
