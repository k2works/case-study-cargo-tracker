package com.example.cargotracker.routingms.interfaces.rest;

import com.example.cargotracker.routingms.infrastructure.persistence.CarrierMovementRecord;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RouteController 統合テスト（US08）。
 *
 * <p>GET /api/v1/routing/candidates で経路候補を返すことを検証する。</p>
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("RouteController 統合テスト")
class RouteControllerIntegrationTest {

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

        // JPYOK → USLAX 直行便
        VoyageRecord voyage = new VoyageRecord();
        voyage.setVoyageNumber("V-RT01");
        voyage.setCarrierCode("YMT");
        voyage.setCarrierName("Test Lines");
        voyage.setShipName("MV Route");
        voyage.setDepartureDate(LocalDateTime.of(2099, 7, 1, 9, 0));
        voyage.setArrivalDate(LocalDateTime.of(2099, 7, 15, 18, 0));
        voyage.setOriginUnlocode("JPYOK");
        voyage.setDestinationUnlocode("USLAX");
        voyage.setStatus("SCHEDULED");
        voyageMapper.insertVoyage(voyage);

        CarrierMovementRecord movement = new CarrierMovementRecord();
        movement.setVoyageNumber("V-RT01");
        movement.setMovementSeq(1);
        movement.setDepartureUnlocode("JPYOK");
        movement.setArrivalUnlocode("USLAX");
        movement.setDepartureTime(LocalDateTime.of(2099, 7, 1, 9, 0));
        movement.setArrivalTime(LocalDateTime.of(2099, 7, 15, 18, 0));
        voyageMapper.insertCarrierMovement(movement);
        voyageMapper.insertAcceptedCargoType("V-RT01", "GENERAL");
    }

    @Test
    void US08_経路候補算出_直行便が候補として返される() throws Exception {
        mockMvc.perform(get("/api/v1/routing/candidates")
                        .param("origin", "JPYOK")
                        .param("destination", "USLAX")
                        .param("arrivalDeadline", "2099-07-31")
                        .param("cargoType", "GENERAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.candidates[0].voyageNumbers[0]").value("V-RT01"));
    }

    @Test
    void US08_経路候補なし_期限超過で空リストを返す() throws Exception {
        mockMvc.perform(get("/api/v1/routing/candidates")
                        .param("origin", "JPYOK")
                        .param("destination", "USLAX")
                        .param("arrivalDeadline", "2099-07-01")
                        .param("cargoType", "GENERAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.candidates").isEmpty());
    }
}
