package com.example.cargotracker.trackingms.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingEventRecord;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingSummaryMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingSummaryRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * TrackingController 統合テスト（US18）。
 *
 * <p>CommandGateway はモックし、Read Model に直接シードデータを入れることで
 * 公開照会 API・JWT 発行 API の振る舞いを検証する。</p>
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("TrackingController 統合テスト")
class TrackingControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommandGateway commandGateway;

    @Autowired
    private TrackingSummaryMapper summaryMapper;

    @Autowired
    private TrackingEventMapper eventMapper;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(commandGateway.send(any(), eq(Object.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private void seedTrackingSummary(String trackingNumber) {
        seedTrackingSummary(trackingNumber, "B-TEST-" + trackingNumber);
    }

    private void seedTrackingSummary(String trackingNumber, String bookingId) {
        var summary = new TrackingSummaryRecord(
                trackingNumber,
                bookingId,
                "IN_TRANSIT",
                "SGSIN",
                null,
                "JPTYO",
                "DEHAM",
                LocalDateTime.of(2026, 8, 10, 14, 30),
                null,
                false,
                LocalDateTime.of(2026, 7, 25, 8, 0),
                null, null, 0L);
        summaryMapper.insert(summary);

        eventMapper.insert(new TrackingEventRecord(
                null,
                trackingNumber,
                LocalDateTime.of(2026, 7, 20, 9, 0),
                null,
                "TRACKING_INITIALIZED",
                "NOT_RECEIVED",
                "JPTYO",
                null, null,
                "追跡が初期化されました",
                "SYSTEM"));
    }

    @Test
    @DisplayName("管理者用 issue-token で URL とトークンが発行される")
    void issueToken_発行成功() throws Exception {
        var trackingNumber = "TRK-A1B2C3D4E5";
        seedTrackingSummary(trackingNumber);

        MvcResult result = mockMvc.perform(post("/api/v1/tracking/_internal/issue-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"" + trackingNumber + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("url").asText()).startsWith("/tracking/" + trackingNumber + "?token=");
        assertThat(body.get("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("発行したトークンで公開照会できる")
    void getTracking_発行から照会の往復が成功() throws Exception {
        var trackingNumber = "TRK-A1B2C3D4E5";
        seedTrackingSummary(trackingNumber);

        MvcResult issued = mockMvc.perform(post("/api/v1/tracking/_internal/issue-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"" + trackingNumber + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(issued.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(get("/api/v1/tracking/{tn}", trackingNumber).param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.currentStatus").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    @DisplayName("不正なトークンで照会すると 401 TOKEN_INVALID")
    void getTracking_不正トークンで401() throws Exception {
        var trackingNumber = "TRK-A1B2C3D4E5";
        seedTrackingSummary(trackingNumber);

        mockMvc.perform(get("/api/v1/tracking/{tn}", trackingNumber).param("token", "not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("追跡番号不在で 404 TRACKING_NOT_FOUND")
    void getTracking_存在しない追跡番号で404() throws Exception {
        // tracking_summary に存在しない追跡番号
        var trackingNumber = "TRK-Z9Y8X7W6V5";

        MvcResult issued = mockMvc.perform(post("/api/v1/tracking/_internal/issue-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"" + trackingNumber + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(issued.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(get("/api/v1/tracking/{tn}", trackingNumber).param("token", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRACKING_NOT_FOUND"));
    }

    @Test
    @DisplayName("URL の追跡番号と JWT 内 tn が異なる場合は 400 TOKEN_TN_MISMATCH")
    void getTracking_tn不一致で400() throws Exception {
        var trackingNumber = "TRK-A1B2C3D4E5";
        seedTrackingSummary(trackingNumber);

        MvcResult issued = mockMvc.perform(post("/api/v1/tracking/_internal/issue-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"" + trackingNumber + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(issued.getResponse().getContentAsString())
                .get("token").asText();

        // 別の追跡番号で照会
        mockMvc.perform(get("/api/v1/tracking/{tn}", "TRK-Z9Y8X7W6V5").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_TN_MISMATCH"));
    }

    @Test
    @DisplayName("S16 GET /api/v1/tracking で全件が最終更新日時降順で返る")
    void listTrackings_全件取得() throws Exception {
        seedTrackingSummary("TRK-A1B2C3D4E5");
        seedTrackingSummary("TRK-Z9Y8X7W6V5");

        mockMvc.perform(get("/api/v1/tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[*].trackingNumber").exists())
                .andExpect(jsonPath("$[*].currentStatus").exists())
                .andExpect(jsonPath("$[*].originUnlocode").exists())
                .andExpect(jsonPath("$[*].destinationUnlocode").exists());
    }

    @Test
    @DisplayName("TI06 PUT /api/v1/tracking/{tn}/status で UpdateTransportStatusCommand が送信される")
    void updateStatus_CommandGatewayへ送信() throws Exception {
        var trackingNumber = "TRK-S1T2A3T4U5";
        seedTrackingSummary(trackingNumber);

        mockMvc.perform(put("/api/v1/tracking/{tn}/status", trackingNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "newStatus": "IN_TRANSIT",
                                    "unlocode": "SGSIN",
                                    "updatedAt": "2026-07-25T08:00:00",
                                    "operatorId": "admin-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.newStatus").value("IN_TRANSIT"));
    }

    @Test
    @DisplayName("PUT /status で不正な状態を渡すと 400 INVALID_STATUS")
    void updateStatus_未知の状態で400() throws Exception {
        var trackingNumber = "TRK-S1T2A3T4U5";
        seedTrackingSummary(trackingNumber);

        mockMvc.perform(put("/api/v1/tracking/{tn}/status", trackingNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "newStatus": "UNKNOWN_STATUS",
                                    "unlocode": "SGSIN",
                                    "operatorId": "admin-001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS"));
    }

    @Test
    @DisplayName("PUT /status で tracking_summary 未初期化なら 404 TRACKING_NOT_FOUND")
    void updateStatus_未初期化なら404() throws Exception {
        // seed しない（tracking_summary に行が無い状態）
        var trackingNumber = "TRK-NOTFOUND123";

        mockMvc.perform(put("/api/v1/tracking/{tn}/status", trackingNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "newStatus": "IN_TRANSIT",
                                    "unlocode": "SGSIN",
                                    "operatorId": "admin-001"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRACKING_NOT_FOUND"));
    }

    @Test
    @DisplayName("内部 initialize API で CommandGateway に InitializeTrackingCommand が送信される")
    void initialize_CommandGatewayへ送信() throws Exception {
        var trackingNumber = "TRK-N1E2W3T4R5";

        mockMvc.perform(post("/api/v1/tracking/_internal/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trackingNumber": "%s",
                                    "bookingId": "B-TEST-002",
                                    "originUnlocode": "JPTYO",
                                    "destinationUnlocode": "DEHAM",
                                    "estimatedArrival": "2026-08-10T14:30:00",
                                    "voyageNumber": "V-MOL-001"
                                }
                                """.formatted(trackingNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber));
    }
}
