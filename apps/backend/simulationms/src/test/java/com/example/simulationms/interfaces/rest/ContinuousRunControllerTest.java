package com.example.simulationms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.auth.AuthenticatedUser;
import com.example.simulationms.application.internal.commandservices.ContinuousRunScheduler;
import com.example.simulationms.domain.model.aggregates.ContinuousRunSession;
import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import com.example.simulationms.domain.repository.ContinuousRunSessionRepository;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 継続実行の開始・停止・状態（US37）。
 *
 * <p><strong>種を返すこと</strong>をここで固定する。返さないと、落ちた実行を
 * 再現する手段が画面から消える——種を記録していても、読めなければ意味が無い。
 */
@WebMvcTest(ContinuousRunController.class)
@org.springframework.test.context.TestPropertySource(
        properties = "app.simulation.enabled=true")
@DisplayName("継続実行 API")
class ContinuousRunControllerTest {

    private static final Instant STARTED = Instant.parse("2026-12-07T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContinuousRunScheduler scheduler;

    @MockitoBean
    private ContinuousRunSessionRepository sessions;

    @MockitoBean
    private SimulationRunRepository runs;

    private static ContinuousRunSession session() {
        return ContinuousRunSession.start(SessionId.of("SES-20261207-0001"), Seed.of(20261207L),
                ContinuousRunPolicy.of(30, 3, BigDecimal.valueOf(0.20)), "admin01", STARTED);
    }

    @Nested
    @DisplayName("システム管理者として")
    class AsAdmin {

        @Test
        @DisplayName("継続実行を開始でき、使った種が返る")
        void startsAndReturnsTheSeed() throws Exception {
            given(sessions.findActive()).willReturn(Optional.empty());
            given(scheduler.start(any(), any(), anyString())).willReturn(session());

            mockMvc.perform(post("/api/v1/simulations/sessions")
                            .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"intervalSeconds": 30, "maxConcurrent": 3,
                                     "exceptionRatio": 0.2, "seed": 20261207}
                                    """))
                    .andExpect(status().isCreated())
                    // **種を返す。**返さないと、落ちた実行を再現する手段が画面から消える
                    .andExpect(jsonPath("$.seed").value(20261207L))
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.statusLabel").value("実行中"));
        }

        /**
         * <strong>2 つ動かさない。</strong>どちらの種で何が流れたのかを追えなくなり、
         * 再現の手がかりが消える。
         */
        @Test
        @DisplayName("既に動いていれば断り、動いているセッションを教える")
        void refusesWhenAlreadyRunning() throws Exception {
            given(sessions.findActive()).willReturn(Optional.of(session()));

            mockMvc.perform(post("/api/v1/simulations/sessions")
                            .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"intervalSeconds\": 30, \"maxConcurrent\": 3,"
                                    + " \"exceptionRatio\": 0.2}"))
                    .andExpect(status().isConflict());
        }

        /** <strong>上限を超える設定は断る</strong>（US37-2）。 */
        @Test
        @DisplayName("上限を超える設定は断る")
        void rejectsSettingsBeyondTheLimit() throws Exception {
            given(sessions.findActive()).willReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/simulations/sessions")
                            .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"intervalSeconds\": 30, \"maxConcurrent\": 999,"
                                    + " \"exceptionRatio\": 0.2}"))
                    .andExpect(status().isBadRequest());
        }

        /** 「止めた」と「止まった」は違う（[ADR-031] 決定 4）。 */
        @Test
        @DisplayName("停止すると、進行中がある場合は停止処理中が返る")
        void stopReturnsStoppingWhileRunsRemain() throws Exception {
            given(scheduler.stop(any())).willReturn(session().stop(1, STARTED.plusSeconds(60)));

            mockMvc.perform(delete("/api/v1/simulations/sessions/SES-20261207-0001")
                            .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("STOPPING"))
                    .andExpect(jsonPath("$.statusLabel").value("停止処理中"));
        }

        @Test
        @DisplayName("知らないセッションの停止は 404 を返す")
        void stoppingAnUnknownSessionIsNotFound() throws Exception {
            given(scheduler.stop(any()))
                    .willThrow(new IllegalArgumentException("そのセッションはありません"));

            mockMvc.perform(delete("/api/v1/simulations/sessions/SES-20261207-9999")
                            .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                    .andExpect(status().isNotFound());
        }

        /**
         * <strong>統計は失敗した工程の分布まで返す</strong>（US37-8）。
         * 件数だけでは直す場所が決まらない。
         */
        @Test
        @DisplayName("状態と統計を返し、失敗した工程の分布も含む")
        void returnsStatisticsWithFailureDistribution() throws Exception {
            given(sessions.findActive()).willReturn(Optional.of(session()));
            given(runs.findRecent(anyInt())).willReturn(List.of(failedRun()));

            mockMvc.perform(get("/api/v1/simulations/sessions/active")
                            .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.session.sessionId").value("SES-20261207-0001"))
                    .andExpect(jsonPath("$.statistics.total").value(1))
                    .andExpect(jsonPath("$.statistics.failed").value(1))
                    .andExpect(jsonPath("$.statistics.failuresByStep[0].step")
                            .value("REGISTER_BOOKING"))
                    .andExpect(jsonPath("$.statistics.failuresByStep[0].label")
                            .value("予約登録"))
                    .andExpect(jsonPath("$.statistics.failuresByStep[0].count").value(1));
        }

        /** 動いていなくても統計は読める。**動いていないことも知らせる**。 */
        @Test
        @DisplayName("動いていなければ、セッションは空で返る")
        void returnsNoSessionWhenNothingIsRunning() throws Exception {
            given(sessions.findActive()).willReturn(Optional.empty());
            given(runs.findRecent(anyInt())).willReturn(List.of());

            mockMvc.perform(get("/api/v1/simulations/sessions/active")
                            .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.session").doesNotExist())
                    .andExpect(jsonPath("$.statistics.total").value(0));
        }
    }

    @Nested
    @DisplayName("業務の担当者として")
    class AsBusinessUser {

        /** <strong>業務データを作り続ける操作である。</strong>担当者には開かない。 */
        @Test
        @DisplayName("継続実行を開始できない")
        void cannotStart() throws Exception {
            mockMvc.perform(post("/api/v1/simulations/sessions")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"intervalSeconds\": 30, \"maxConcurrent\": 3,"
                                    + " \"exceptionRatio\": 0.2}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("統計も読めない")
        void cannotReadStatistics() throws Exception {
            mockMvc.perform(get("/api/v1/simulations/sessions/active")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isForbidden());
        }
    }

    private static SimulationRun failedRun() {
        Scenario scenario = Scenario.of("stats", List.of(ScenarioStep.REGISTER_SHIPPER,
                ScenarioStep.REGISTER_BOOKING));
        return SimulationRun.start(RunId.of("SIM-20261207-0001"), scenario, "admin01", STARTED)
                .withResult(StepResult.succeeded(ScenarioStep.REGISTER_SHIPPER,
                        Duration.ofMillis(10), "1", STARTED))
                .withResult(StepResult.failed(ScenarioStep.REGISTER_BOOKING,
                        Duration.ofMillis(10), "失敗", STARTED));
    }
}
