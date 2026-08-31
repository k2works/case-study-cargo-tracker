package com.example.simulationms.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.auth.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SimulationScenarioController.class)
@DisplayName("シナリオ一覧 API")
class SimulationScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("システム管理者は、実行できるシナリオと工程の並びを読める")
    void returnsScenariosToAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/simulations/scenarios")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("standard-transport"))
                .andExpect(jsonPath("$[0].steps[0].step").value("REGISTER_SHIPPER"))
                .andExpect(jsonPath("$[0].steps[0].role").value("ROLE_SALES"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_SALES", "ROLE_ROUTING", "ROLE_TRACKER", "ROLE_HANDLER",
            "ROLE_ACCOUNTANT", "ROLE_SHIPPER"})
    @DisplayName("業務の担当者は、シナリオ一覧を読めない")
    void rejectsBusinessRoles(String role) throws Exception {
        mockMvc.perform(get("/api/v1/simulations/scenarios")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sim-sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, role))
                .andExpect(status().isForbidden());
    }

    /**
     * <strong>例外シナリオも選べる</strong>（US36-1）。
     *
     * <p>並びを画面が持たないのと同じ理由で、<strong>シナリオの一覧も画面が持たない</strong>。
     * 足したシナリオが画面に出ないと、実装したのに選べないという形になる。
     */
    @Test
    @DisplayName("例外シナリオも一覧に出る")
    void listsExceptionScenarios() throws Exception {
        mockMvc.perform(get("/api/v1/simulations/scenarios")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'delay')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'damage')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'misroute')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'customs-hold')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'cancellation')]").exists());
    }

    /** <strong>正常系が先頭にある。</strong>実演では正常系から見せる。 */
    @Test
    @DisplayName("正常系が先頭に出る")
    void listsTheHappyPathFirst() throws Exception {
        mockMvc.perform(get("/api/v1/simulations/scenarios")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("standard-transport"));
    }
}
