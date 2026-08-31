package com.example.simulationms.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.auth.AuthenticatedUser;
import com.example.simulationms.application.internal.commandservices.RunSimulationUseCase;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * <strong>無効にした環境では、実行そのものを断る</strong>（[ADR-030] 決定 4）。
 *
 * <p>起動時の検査だけに使っていると、「安全側に倒したつもり」で {@code false} にした環境が、
 * そのまま実行を受け付ける——設定の名前が守っていない状態になる。
 */
@WebMvcTest(SimulationRunController.class)
@TestPropertySource(properties = "app.simulation.enabled=false")
@DisplayName("実行を無効にした環境")
class SimulationDisabledRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RunSimulationUseCase runSimulation;

    @MockitoBean
    private SimulationRunRepository runs;

    @Test
    @DisplayName("システム管理者が指示しても、実行を受け付けない")
    void refusesToRun() throws Exception {
        mockMvc.perform(post("/api/v1/simulations")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"standard-transport\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
