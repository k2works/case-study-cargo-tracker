package com.example.cargotracker.handlingms.interfaces.rest;

import com.example.cargotracker.handlingms.domain.model.commands.RegisterHandlingActivityCommand;
import com.example.cargotracker.handlingms.domain.model.commands.UpdateCargoStatusCommand;
import com.example.cargotracker.handlingms.infrastructure.persistence.CargoSnapshotMapper;
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

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HandlingController 統合テスト（US15）。
 *
 * <p>CommandGateway は {@code @MockitoBean} で置き換え、Axon Server への接続を回避する。</p>
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("HandlingController 統合テスト")
class HandlingControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommandGateway commandGateway;

    @Autowired
    private CargoSnapshotMapper cargoSnapshotMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(commandGateway.send(any(), eq(Object.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private void registerSnapshot(String trackingNumber, String origin, String destination) {
        var record = new com.example.cargotracker.handlingms.infrastructure.persistence.CargoSnapshotRecord();
        record.setBookingId("B-TEST-001");
        record.setTrackingNumber(trackingNumber);
        record.setOriginUnlocode(origin);
        record.setDestinationUnlocode(destination);
        record.setCargoType("GENERAL");
        record.setArrivalDeadline(java.time.LocalDate.of(2099, 12, 31));
        record.setBookingStatus("TRACKING_ISSUED");
        cargoSnapshotMapper.upsert(record);
    }

    @Test
    @DisplayName("CargoSnapshot 再登録時は bookingId で upsert され最新値に更新される")
    void cargoSnapshot再登録でupsert更新() throws Exception {
        registerSnapshot("TRK-20260720-FIRST001", "JPTYO", "DEHAM");
        registerSnapshot("TRK-20260720-SECOND01", "JPTYO", "NLRTM");

        var stored = cargoSnapshotMapper.findByBookingId("B-TEST-001");
        assertThat(stored).isNotNull();
        assertThat(stored.getTrackingNumber()).isEqualTo("TRK-20260720-SECOND01");
        assertThat(stored.getDestinationUnlocode()).isEqualTo("NLRTM");
        assertThat(stored.getBookingStatus()).isEqualTo("TRACKING_ISSUED");
    }

    @Test
    @DisplayName("US15: POST /api/v1/handling/activities で受領作業を登録できる（201）")
    void 荷役作業登録_受領() throws Exception {
        String trk = "TRK-20260720-ABC12345";
        registerSnapshot(trk, "JPTYO", "DEHAM");

        mockMvc.perform(post("/api/v1/handling/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trackingNumber": "%s",
                                    "handlingType": "RECEIVE",
                                    "unlocode": "JPTYO",
                                    "occurredAt": "2026-07-20T09:00:00",
                                    "operatorId": "handler-001"
                                }
                                """.formatted(trk)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value(trk))
                .andExpect(jsonPath("$.handlingType").value("RECEIVE"))
                .andExpect(jsonPath("$.unlocode").value("JPTYO"))
                .andExpect(jsonPath("$.unexpected").value(false));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).send(captor.capture(), eq(Object.class));
        assertThat(captor.getValue()).isInstanceOf(RegisterHandlingActivityCommand.class);
    }

    @Test
    @DisplayName("US15 受入条件6: 追跡番号が存在しない場合 404 を返す")
    void 追跡番号不在で404() throws Exception {
        mockMvc.perform(post("/api/v1/handling/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trackingNumber": "TRK-20260720-NOTFOUND",
                                    "handlingType": "RECEIVE",
                                    "unlocode": "JPTYO",
                                    "occurredAt": "2026-07-20T09:00:00",
                                    "operatorId": "handler-001"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("追跡番号が存在しません: TRK-20260720-NOTFOUND"));
    }

    @Test
    @DisplayName("US15 受入条件7: 予定外場所だと unexpected=true でレスポンスする")
    void 予定外場所で警告() throws Exception {
        String trk = "TRK-20260720-XYZ98765";
        registerSnapshot(trk, "JPTYO", "DEHAM");

        // 東京発・ハンブルク行の RECEIVE をシンガポールで登録 → 予定外
        mockMvc.perform(post("/api/v1/handling/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trackingNumber": "%s",
                                    "handlingType": "RECEIVE",
                                    "unlocode": "SGSIN",
                                    "occurredAt": "2026-07-20T09:00:00",
                                    "operatorId": "handler-001"
                                }
                                """.formatted(trk)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unexpected").value(true));
    }

    @Test
    @DisplayName("US15 受入条件2/3: LOAD 種別は voyageNumber が必須でないと 400")
    void LOAD種別はvoyageNumber必須() throws Exception {
        String trk = "TRK-20260720-LOAD0001";
        registerSnapshot(trk, "JPTYO", "DEHAM");

        mockMvc.perform(post("/api/v1/handling/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trackingNumber": "%s",
                                    "handlingType": "LOAD",
                                    "unlocode": "JPTYO",
                                    "occurredAt": "2026-07-20T14:00:00",
                                    "operatorId": "handler-001"
                                }
                                """.formatted(trk)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("LOAD 種別の作業には voyageNumber が必須です"));
    }

    @Test
    @DisplayName("GET /api/v1/handling/activities/{trackingNumber} で空配列を返す（履歴なし）")
    void 履歴照会_空() throws Exception {
        mockMvc.perform(get("/api/v1/handling/activities/TRK-EMPTY"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("US16 受入1/2: CLAIM 種別は claimVerification が必須でないと 400")
    void CLAIM種別はclaimVerification必須() throws Exception {
        String trk = "TRK-20260810-CLAIM001";
        registerSnapshot(trk, "JPTYO", "DEHAM");

        mockMvc.perform(post("/api/v1/handling/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trackingNumber": "%s",
                                    "handlingType": "CLAIM",
                                    "unlocode": "DEHAM",
                                    "occurredAt": "2026-08-10T14:30:00",
                                    "operatorId": "handler-002"
                                }
                                """.formatted(trk)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("CLAIM 種別の作業には claimVerification が必須です（US16）"));
    }

    @Test
    @DisplayName("US16 受入1/2: CLAIM + 確認コードで引取作業を登録できる（201）")
    void 引取作業_確認コード() throws Exception {
        String trk = "TRK-20260810-CLAIM002";
        registerSnapshot(trk, "JPTYO", "DEHAM");

        mockMvc.perform(post("/api/v1/handling/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trackingNumber": "%s",
                                    "handlingType": "CLAIM",
                                    "unlocode": "DEHAM",
                                    "occurredAt": "2026-08-10T14:30:00",
                                    "operatorId": "handler-002",
                                    "claimVerification": {
                                        "consigneeName": "John Doe",
                                        "confirmationCode": "AX9-2K7"
                                    }
                                }
                                """.formatted(trk)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.handlingType").value("CLAIM"))
                .andExpect(jsonPath("$.unexpected").value(false));
    }

    @Test
    @DisplayName("US17 受入1: GET /activities/{trk}/snapshot で現在の貨物概要を返す")
    void 貨物概要照会() throws Exception {
        String trk = "TRK-20260725-SNAPSHOT1";
        registerSnapshot(trk, "JPTYO", "DEHAM");

        mockMvc.perform(get("/api/v1/handling/activities/" + trk + "/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trk))
                .andExpect(jsonPath("$.originUnlocode").value("JPTYO"))
                .andExpect(jsonPath("$.destinationUnlocode").value("DEHAM"));
    }

    @Test
    @DisplayName("US17 受入1: 追跡番号が存在しない貨物概要照会で 404")
    void 貨物概要_追跡番号不在で404() throws Exception {
        mockMvc.perform(get("/api/v1/handling/activities/TRK-20260725-NOTFOUND/snapshot"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US17 受入2/3: PUT /activities/{trk}/status で UpdateCargoStatusCommand が送信される")
    void 状態手動更新() throws Exception {
        String trk = "TRK-20260725-UPDATE01";
        registerSnapshot(trk, "JPTYO", "DEHAM");

        mockMvc.perform(put("/api/v1/handling/activities/" + trk + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "newStatus": "IN_TRANSIT",
                                    "unlocode": "SGSIN",
                                    "updatedAt": "2026-07-25T08:00:00",
                                    "operatorId": "tracker-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trk))
                .andExpect(jsonPath("$.newStatus").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.unlocode").value("SGSIN"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).send(captor.capture(), eq(Object.class));
        assertThat(captor.getValue()).isInstanceOf(UpdateCargoStatusCommand.class);
    }

    @Test
    @DisplayName("US17: 許可されていない状態への更新で 400")
    void 状態手動更新_不正値で400() throws Exception {
        String trk = "TRK-20260725-UPDATE02";
        registerSnapshot(trk, "JPTYO", "DEHAM");

        mockMvc.perform(put("/api/v1/handling/activities/" + trk + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "newStatus": "INVALID_STATUS",
                                    "unlocode": "SGSIN",
                                    "updatedAt": "2026-07-25T08:00:00",
                                    "operatorId": "tracker-001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("INVALID_STATUS")));
    }

    @Test
    @DisplayName("US17: 追跡番号が存在しない状態手動更新で 404")
    void 状態手動更新_追跡番号不在で404() throws Exception {
        mockMvc.perform(put("/api/v1/handling/activities/TRK-20260725-NOTFOUND/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "newStatus": "IN_TRANSIT",
                                    "unlocode": "SGSIN",
                                    "updatedAt": "2026-07-25T08:00:00",
                                    "operatorId": "tracker-001"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US16 受入1: CLAIM + 署名 ref で引取作業を登録できる（confirmationCode 不要）")
    void 引取作業_署名() throws Exception {
        String trk = "TRK-20260810-CLAIM003";
        registerSnapshot(trk, "JPTYO", "DEHAM");

        mockMvc.perform(post("/api/v1/handling/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trackingNumber": "%s",
                                    "handlingType": "CLAIM",
                                    "unlocode": "DEHAM",
                                    "occurredAt": "2026-08-10T14:30:00",
                                    "operatorId": "handler-002",
                                    "claimVerification": {
                                        "consigneeName": "Jane Smith",
                                        "signatureRef": "s3://signatures/2026/08/10/jane.png"
                                    }
                                }
                                """.formatted(trk)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.handlingType").value("CLAIM"));
    }
}
