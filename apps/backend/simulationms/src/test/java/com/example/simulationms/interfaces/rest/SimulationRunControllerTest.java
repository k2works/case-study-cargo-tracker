package com.example.simulationms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.auth.AuthenticatedUser;
import com.example.simulationms.application.internal.commandservices.RunSimulationUseCase;
import com.example.simulationms.application.internal.commandservices.SimulationAlreadyRunningException;
import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 実行の指示と確認（US34・US35）。
 *
 * <p>実行はシステム管理者だけに開く。業務データを作る操作であり、業務の担当者が
 * 誤って踏める場所には置かない。
 */
@WebMvcTest(SimulationRunController.class)
@org.springframework.test.context.TestPropertySource(
        properties = "app.simulation.enabled=true")
@DisplayName("シミュレーション実行 API")
class SimulationRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RunSimulationUseCase runSimulation;

    @MockitoBean
    private SimulationRunRepository runs;

    private static SimulationRun finished() {
        Scenario scenario = Scenario.of("short", List.of(ScenarioStep.REGISTER_SHIPPER));
        return SimulationRun.start(RunId.of("SIM-20261116-0001"), scenario, "admin01",
                        Instant.parse("2026-11-16T01:00:00Z"))
                .withResult(StepResult.succeeded(ScenarioStep.REGISTER_SHIPPER,
                        Duration.ofMillis(120), "42", Instant.parse("2026-11-16T01:00:01Z")));
    }

    @Test
    @DisplayName("システム管理者は、シナリオを指定して実行できる")
    void startsARun() throws Exception {
        given(runSimulation.run(any(), anyString())).willReturn(finished());

        mockMvc.perform(post("/api/v1/simulations")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"standard-transport\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value("SIM-20261116-0001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.steps[0].step").value("REGISTER_SHIPPER"))
                .andExpect(jsonPath("$.steps[0].outcome").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[0].createdIdentifier").value("42"));
    }

    /**
     * <strong>断るだけで終わらせない</strong>（US34-5）。
     *
     * <p>実行中の ID を返し、そこへ行けるようにする。気づく手段は次の行動へ繋ぐ。
     */
    @Test
    @DisplayName("同じシナリオが実行中なら 409 で断り、実行中の ID を案内する")
    void refusesADoubleRun() throws Exception {
        willThrow(new SimulationAlreadyRunningException(RunId.of("SIM-20261116-0009")))
                .given(runSimulation).run(any(), anyString());

        mockMvc.perform(post("/api/v1/simulations")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"standard-transport\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.runningRunId").value("SIM-20261116-0009"));
    }

    @Test
    @DisplayName("知らないシナリオは、名前を挙げて断る")
    void rejectsAnUnknownScenario() throws Exception {
        mockMvc.perform(post("/api/v1/simulations")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"unknown\"}"))
                .andExpect(status().isBadRequest());
    }

    /** 認可は入力の検査より先に置く（[ADR-016]）。権限の無い相手に入力仕様を教えない。 */
    @ParameterizedTest
    @ValueSource(strings = {"ROLE_SALES", "ROLE_ROUTING", "ROLE_TRACKER", "ROLE_HANDLER",
            "ROLE_ACCOUNTANT", "ROLE_SHIPPER"})
    @DisplayName("業務の担当者は、実行を指示できない（入力の誤りより先に断る）")
    void rejectsBusinessRoles(String role) throws Exception {
        mockMvc.perform(post("/api/v1/simulations")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sim-sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("実行の一覧を新しい順に読める")
    void listsRecentRuns() throws Exception {
        given(runs.findRecent(anyInt())).willReturn(List.of(finished()));

        mockMvc.perform(get("/api/v1/simulations")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value("SIM-20261116-0001"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("実行の詳細を工程ごとに読める")
    void showsOneRun() throws Exception {
        given(runs.findByRunId(RunId.of("SIM-20261116-0001"))).willReturn(Optional.of(finished()));

        mockMvc.perform(get("/api/v1/simulations/SIM-20261116-0001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].label").value("荷主登録"))
                .andExpect(jsonPath("$.steps[0].elapsedMs").value(120));
    }

    @Test
    @DisplayName("知らない実行 ID は 404 で、形が違う ID も 404 にする")
    void returnsNotFoundForUnknownRuns() throws Exception {
        given(runs.findByRunId(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/simulations/SIM-20261116-9999")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isNotFound());

        // 形が違う ID は、解析の失敗を 500 にせず「見つかりません」に落とす。
        // **catch は解析だけを囲む**——読み出しまで囲むと、復元の例外が原因ごと消える
        mockMvc.perform(get("/api/v1/simulations/not-a-run-id")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isNotFound());
    }

    /** 例外シナリオも実行を指示できる（US36-1）。 */
    @org.junit.jupiter.api.Test
    @DisplayName("例外シナリオを指定して実行できる")
    void runsAnExceptionScenario() throws Exception {
        given(runSimulation.run(org.mockito.ArgumentMatchers.argThat(
                        scenario -> scenario != null && "misroute".equals(scenario.id())),
                org.mockito.ArgumentMatchers.eq("admin01")))
                .willReturn(SimulationRun.start(RunId.of("SIM-20261116-0003"),
                        Scenario.exceptionScenarios().stream()
                                .filter(candidate -> candidate.id().equals("misroute"))
                                .findFirst().orElseThrow(),
                        "admin01", java.time.Instant.parse("2026-11-16T01:00:00Z")));

        mockMvc.perform(post("/api/v1/simulations")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\": \"misroute\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scenarioId").value("misroute"));
    }
}
