package com.example.cargotracker.routingms.interfaces.rest;

import com.example.cargotracker.routingms.infrastructure.persistence.CarrierMovementRecord;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RouteSelectionController 統合テスト（US09）。
 *
 * <p>POST /api/v1/routing/select で経路候補を選択し、
 * 選択した経路の Legs 情報が返されることを検証する。</p>
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("RouteSelectionController 統合テスト")
class RouteSelectionControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommandGateway commandGateway;

    @Autowired
    private VoyageMapper voyageMapper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        VoyageRecord voyage = new VoyageRecord();
        voyage.setVoyageNumber("V-SEL01");
        voyage.setCarrierCode("YMT");
        voyage.setCarrierName("Test Lines");
        voyage.setShipName("MV Select");
        voyage.setDepartureDate(LocalDateTime.of(2099, 8, 1, 9, 0));
        voyage.setArrivalDate(LocalDateTime.of(2099, 8, 15, 18, 0));
        voyage.setOriginUnlocode("JPYOK");
        voyage.setDestinationUnlocode("USLAX");
        voyage.setStatus("SCHEDULED");
        voyageMapper.insertVoyage(voyage);

        CarrierMovementRecord movement = new CarrierMovementRecord();
        movement.setVoyageNumber("V-SEL01");
        movement.setMovementSeq(1);
        movement.setDepartureUnlocode("JPYOK");
        movement.setArrivalUnlocode("USLAX");
        movement.setDepartureTime(LocalDateTime.of(2099, 8, 1, 9, 0));
        movement.setArrivalTime(LocalDateTime.of(2099, 8, 15, 18, 0));
        voyageMapper.insertCarrierMovement(movement);
        voyageMapper.insertAcceptedCargoType("V-SEL01", "GENERAL");
    }

    @Test
    @DisplayName("US09_経路選択_候補インデックス0を選択すると経路Legsが返される")
    void US09_経路選択_候補インデックス0を選択すると経路Legsが返される() throws Exception {
        var request = Map.of(
                "origin", "JPYOK",
                "destination", "USLAX",
                "arrivalDeadline", "2099-08-31",
                "cargoType", "GENERAL",
                "selectedIndex", 0
        );
        mockMvc.perform(post("/api/v1/routing/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs").isArray())
                .andExpect(jsonPath("$.legs[0].voyageNumber").value("V-SEL01"))
                .andExpect(jsonPath("$.legs[0].loadUnlocode").value("JPYOK"))
                .andExpect(jsonPath("$.legs[0].unloadUnlocode").value("USLAX"));
    }

    @Test
    @DisplayName("US09_経路選択_候補なし_404を返す")
    void US09_経路選択_候補なし_404を返す() throws Exception {
        var request = Map.of(
                "origin", "JPYOK",
                "destination", "USLAX",
                "arrivalDeadline", "2099-08-01",
                "cargoType", "GENERAL",
                "selectedIndex", 0
        );
        mockMvc.perform(post("/api/v1/routing/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US09_経路選択_インデックス範囲外_400を返す")
    void US09_経路選択_インデックス範囲外_400を返す() throws Exception {
        var request = Map.of(
                "origin", "JPYOK",
                "destination", "USLAX",
                "arrivalDeadline", "2099-08-31",
                "cargoType", "GENERAL",
                "selectedIndex", 99
        );
        mockMvc.perform(post("/api/v1/routing/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
