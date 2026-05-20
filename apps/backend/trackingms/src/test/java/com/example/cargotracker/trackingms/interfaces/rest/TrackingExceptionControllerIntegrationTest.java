package com.example.cargotracker.trackingms.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.trackingms.domain.model.commands.RegisterTrackingExceptionCommand;
import com.example.cargotracker.trackingms.domain.model.commands.ResolveTrackingExceptionCommand;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingEventRecord;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingSummaryMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingSummaryRecord;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
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

/**
 * TrackingExceptionController 統合テスト（US19 / US20）。
 *
 * <p>ArgumentCaptor でコマンドの内容を検証し、
 * ExceptionType enum の型安全な扱いを確認する。</p>
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("TrackingExceptionController 統合テスト")
class TrackingExceptionControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommandGateway commandGateway;

    @Autowired
    private TrackingSummaryMapper summaryMapper;

    @Autowired
    private TrackingEventMapper eventMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(commandGateway.send(any(), eq(Object.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private void seedTrackingSummary(String trackingNumber) {
        var summary = new TrackingSummaryRecord(
                trackingNumber,
                "B-TEST-" + trackingNumber,
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
    @DisplayName("DELAY 例外登録で RegisterTrackingExceptionCommand の内容を ArgumentCaptor で検証する")
    void registerException_ArgumentCaptorでコマンド内容を検証() throws Exception {
        var trackingNumber = "TRK-20260810-N1E2W3T4";
        seedTrackingSummary(trackingNumber);

        mockMvc.perform(post("/api/v1/tracking/{tn}/exceptions", trackingNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "exceptionType": "DELAY",
                                    "occurredUnlocode": "SGSIN",
                                    "description": "港湾渋滞による遅延",
                                    "operatorId": "admin-001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.exceptionId").exists());

        var captor = ArgumentCaptor.forClass(RegisterTrackingExceptionCommand.class);
        verify(commandGateway).send(captor.capture(), eq(Object.class));

        var command = captor.getValue();
        assertThat(command.trackingNumber()).isEqualTo(trackingNumber);
        assertThat(command.exceptionType()).isEqualTo("DELAY");
        assertThat(command.occurredUnlocode()).isEqualTo("SGSIN");
        assertThat(command.description()).isEqualTo("港湾渋滞による遅延");
        assertThat(command.operatorId()).isEqualTo("admin-001");
        assertThat(command.exceptionId()).isNotBlank();
    }

    @Test
    @DisplayName("LOSS 例外登録で operatorId が null の場合 system が使われる")
    void registerException_LOSSでoperatorIdNullはsystemになる() throws Exception {
        var trackingNumber = "TRK-20260810-LOSS0001";
        seedTrackingSummary(trackingNumber);

        mockMvc.perform(post("/api/v1/tracking/{tn}/exceptions", trackingNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "exceptionType": "LOSS",
                                    "occurredUnlocode": "SGSIN",
                                    "description": "紛失"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exceptionId").exists());

        var captor = ArgumentCaptor.forClass(RegisterTrackingExceptionCommand.class);
        verify(commandGateway).send(captor.capture(), eq(Object.class));

        var command = captor.getValue();
        assertThat(command.exceptionType()).isEqualTo("LOSS");
        assertThat(command.operatorId()).isEqualTo("system");
    }

    @Test
    @DisplayName("未知の exceptionType を渡すと 400 INVALID_EXCEPTION_TYPE")
    void registerException_未知の種別で400() throws Exception {
        var trackingNumber = "TRK-20260810-N1E2W3T4";
        seedTrackingSummary(trackingNumber);

        mockMvc.perform(post("/api/v1/tracking/{tn}/exceptions", trackingNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "exceptionType": "UNKNOWN_TYPE",
                                    "occurredUnlocode": "SGSIN",
                                    "description": "テスト"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_EXCEPTION_TYPE"));
    }

    @Test
    @DisplayName("例外登録 API で追跡番号が存在しない場合 404 を返す")
    void registerException_存在しない追跡番号で404() throws Exception {
        mockMvc.perform(post("/api/v1/tracking/{tn}/exceptions", "TRK-99999999-NOTEXIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "exceptionType": "DELAY",
                                    "occurredUnlocode": "SGSIN",
                                    "description": "テスト"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("例外一覧 API で登録済みの例外一覧を取得できる")
    void listExceptions_例外一覧取得() throws Exception {
        var trackingNumber = "TRK-20260810-N1E2W3T4";
        seedTrackingSummary(trackingNumber);

        mockMvc.perform(get("/api/v1/tracking/{tn}/exceptions", trackingNumber))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("例外一覧 API で追跡番号が存在しない場合 404 を返す")
    void listExceptions_存在しない追跡番号で404() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{tn}/exceptions", "TRK-99999999-NOTEXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("例外解決 API で ResolveTrackingExceptionCommand の内容を ArgumentCaptor で検証する")
    void resolveException_ArgumentCaptorでコマンド内容を検証() throws Exception {
        var trackingNumber = "TRK-20260810-N1E2W3T4";
        seedTrackingSummary(trackingNumber);

        mockMvc.perform(patch("/api/v1/tracking/{tn}/exceptions/{exId}/resolve",
                        trackingNumber, "EX-20260810-00000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "resolution": "代替便手配済み",
                                    "operatorId": "admin-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber));

        var captor = ArgumentCaptor.forClass(ResolveTrackingExceptionCommand.class);
        verify(commandGateway).send(captor.capture(), eq(Object.class));

        var command = captor.getValue();
        assertThat(command.trackingNumber()).isEqualTo(trackingNumber);
        assertThat(command.exceptionId()).isEqualTo("EX-20260810-00000001");
        assertThat(command.resolution()).isEqualTo("代替便手配済み");
        assertThat(command.operatorId()).isEqualTo("admin-001");
    }

    @Test
    @DisplayName("例外解決 API で追跡番号が存在しない場合 404 を返す")
    void resolveException_存在しない追跡番号で404() throws Exception {
        mockMvc.perform(patch("/api/v1/tracking/{tn}/exceptions/{exId}/resolve",
                        "TRK-99999999-NOTEXIST", "EX-00000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "resolution": "テスト"
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
