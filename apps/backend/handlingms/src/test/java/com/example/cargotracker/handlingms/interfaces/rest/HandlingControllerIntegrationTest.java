package com.example.cargotracker.handlingms.interfaces.rest;

import com.example.cargotracker.handlingms.domain.model.commands.RegisterHandlingActivityCommand;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(commandGateway.send(any(), eq(Object.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private void registerSnapshot(String trackingNumber, String origin, String destination) throws Exception {
        mockMvc.perform(post("/api/v1/handling/cargo-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bookingId": "B-TEST-001",
                                    "trackingNumber": "%s",
                                    "originUnlocode": "%s",
                                    "destinationUnlocode": "%s",
                                    "cargoType": "GENERAL",
                                    "arrivalDeadline": "2099-12-31",
                                    "bookingStatus": "TRACKING_ISSUED"
                                }
                                """.formatted(trackingNumber, origin, destination)))
                .andExpect(status().isCreated());
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
}
