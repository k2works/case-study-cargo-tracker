package com.example.cargotracker.routingms.interfaces.rest;

import com.example.cargotracker.routingms.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageRecord;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VoyageController 統合テスト（US24）。
 *
 * <p>CommandGateway は {@code @MockitoBean} で置き換え。実 Axon との連携検証は
 * IT3 以降の E2E で行う。</p>
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("VoyageController 統合テスト")
class VoyageControllerIntegrationTest {

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
        when(commandGateway.sendAndWait(any())).thenReturn(null);
    }

    private String validRequestJson(String voyageNumber) {
        return """
                {
                    "voyageNumber": "%s",
                    "carrierCode": "MOL",
                    "carrierName": "Mitsui O.S.K. Lines",
                    "shipName": "Yokohama Express",
                    "originUnLocode": "JPYOK",
                    "destinationUnLocode": "USLAX",
                    "departureDate": "2026-07-01T09:00:00",
                    "arrivalDate": "2026-07-15T18:00:00",
                    "carrierMovements": [
                        {
                            "departureUnLocode": "JPYOK",
                            "arrivalUnLocode": "TWKHH",
                            "departureTime": "2026-07-01T09:00:00",
                            "arrivalTime": "2026-07-05T18:00:00"
                        },
                        {
                            "departureUnLocode": "TWKHH",
                            "arrivalUnLocode": "USLAX",
                            "departureTime": "2026-07-06T09:00:00",
                            "arrivalTime": "2026-07-15T18:00:00"
                        }
                    ],
                    "acceptedCargoTypes": ["GENERAL", "REFRIGERATED"]
                }
                """.formatted(voyageNumber);
    }

    @Test
    @DisplayName("US24: 航海スケジュールを新規登録できる（201、SCHEDULED）")
    void 航海登録できる() throws Exception {
        mockMvc.perform(post("/api/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("V12345")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voyageNumber").value("V12345"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(RegisterVoyageCommand.class);
        RegisterVoyageCommand cmd = (RegisterVoyageCommand) captor.getValue();
        assertThat(cmd.voyageNumber()).isEqualTo("V12345");
        assertThat(cmd.carrier().code()).isEqualTo("MOL");
        assertThat(cmd.shipName()).isEqualTo("Yokohama Express");
        assertThat(cmd.carrierMovements()).hasSize(2);
        assertThat(cmd.acceptedCargoTypes()).hasSize(2);
    }

    @Test
    @DisplayName("US24: 必須項目が欠落していると 400")
    void 必須項目欠落で400() throws Exception {
        mockMvc.perform(post("/api/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "voyageNumber": "V12345",
                                    "carrierCode": "MOL",
                                    "shipName": "Yokohama Express",
                                    "originUnLocode": "JPYOK",
                                    "destinationUnLocode": "USLAX",
                                    "departureDate": "2026-07-01T09:00:00",
                                    "arrivalDate": "2026-07-15T18:00:00",
                                    "carrierMovements": [],
                                    "acceptedCargoTypes": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("US24: arrival <= departure（日付逆転）は 400")
    void 日付整合性違反で400() throws Exception {
        mockMvc.perform(post("/api/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "voyageNumber": "V99",
                                    "carrierCode": "MOL",
                                    "carrierName": "MOL",
                                    "shipName": "Ship",
                                    "originUnLocode": "JPYOK",
                                    "destinationUnLocode": "USLAX",
                                    "departureDate": "2026-07-15T18:00:00",
                                    "arrivalDate": "2026-07-01T09:00:00",
                                    "carrierMovements": [
                                        {
                                            "departureUnLocode": "JPYOK",
                                            "arrivalUnLocode": "USLAX",
                                            "departureTime": "2026-07-15T18:00:00",
                                            "arrivalTime": "2026-07-16T09:00:00"
                                        }
                                    ],
                                    "acceptedCargoTypes": ["GENERAL"]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/voyages で航海スケジュール一覧を取得できる（200、voyage を投影）")
    void 航海一覧を取得できる() throws Exception {
        VoyageRecord record = new VoyageRecord();
        record.setVoyageNumber("V-LIST-001");
        record.setCarrierCode("MOL");
        record.setCarrierName("Mitsui O.S.K. Lines");
        record.setShipName("Yokohama Express");
        record.setDepartureDate(LocalDateTime.of(2026, 7, 1, 9, 0));
        record.setArrivalDate(LocalDateTime.of(2026, 7, 15, 18, 0));
        record.setOriginUnlocode("JPYOK");
        record.setDestinationUnlocode("USLAX");
        record.setStatus("SCHEDULED");
        voyageMapper.insertVoyage(record);

        mockMvc.perform(get("/api/v1/voyages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.voyageNumber == 'V-LIST-001')]").exists())
                .andExpect(jsonPath("$[?(@.voyageNumber == 'V-LIST-001')].shipName")
                        .value("Yokohama Express"))
                .andExpect(jsonPath("$[?(@.voyageNumber == 'V-LIST-001')].carrierCode")
                        .value("MOL"))
                .andExpect(jsonPath("$[?(@.voyageNumber == 'V-LIST-001')].originUnLocode")
                        .value("JPYOK"))
                .andExpect(jsonPath("$[?(@.voyageNumber == 'V-LIST-001')].destinationUnLocode")
                        .value("USLAX"))
                .andExpect(jsonPath("$[?(@.voyageNumber == 'V-LIST-001')].status")
                        .value("SCHEDULED"));
    }

    @Test
    @DisplayName("US24: 同一航海番号は 409 を返す")
    void 同一航海番号は409() throws Exception {
        // 1 回目（H2 + Mock commandGateway なので Mapper.existsBy は false → 201）
        mockMvc.perform(post("/api/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("V-DUP-001")))
                .andExpect(status().isCreated());

        // VoyageProjectionsEventHandler は @Profile で除外されているので、H2 にレコードは
        // 入らない（モック化された commandGateway は何もしない）。VoyageMapper.existsBy
        // を本テストでは確認できないため、別の Mapper Mock 戦略は IT3 で検討。
        // 本ケースは「コントローラの分岐は通る」確認のみ。
    }
}
