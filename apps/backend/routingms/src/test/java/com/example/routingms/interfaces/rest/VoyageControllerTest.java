package com.example.routingms.interfaces.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VoyageController 統合テスト（H2 インメモリ DB 使用）
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("VoyageController 統合テスト")
class VoyageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String CM_JSON = """
            {"departureLocationUnlocode":"JPTYO","arrivalLocationUnlocode":"CNSHA",
             "departureDate":"2025-01-10T08:00:00+09:00","arrivalDate":"2025-01-12T18:00:00+09:00","seqNumber":0}
            """;

    private String createBody(String voyageNumber) {
        return """
                {"voyageNumber":"%s","carrierMovements":[%s]}
                """.formatted(voyageNumber, CM_JSON);
    }

    @Test
    @DisplayName("POST /api/routing/v1/voyages — 航海を正常に登録できること")
    void shouldCreateVoyage() throws Exception {
        mockMvc.perform(post("/api/routing/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("V001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voyageNumber").value("V001"))
                .andExpect(jsonPath("$.carrierMovements[0].departureLocationUnlocode").value("JPTYO"))
                .andExpect(jsonPath("$.carrierMovements[0].arrivalLocationUnlocode").value("CNSHA"));
    }

    @Test
    @DisplayName("POST /api/routing/v1/voyages — 重複航海番号は 409 を返すこと")
    void shouldReturn409WhenDuplicateVoyageNumber() throws Exception {
        mockMvc.perform(post("/api/routing/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("V002")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/routing/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("V002")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/routing/v1/voyages — 全航海一覧を取得できること")
    void shouldListVoyages() throws Exception {
        mockMvc.perform(post("/api/routing/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("V003")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/routing/v1/voyages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.voyageNumber == 'V003')]").exists());
    }

    @Test
    @DisplayName("GET /api/routing/v1/voyages/{voyageNumber} — 航海詳細を取得できること")
    void shouldGetVoyageByNumber() throws Exception {
        mockMvc.perform(post("/api/routing/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("V004")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/routing/v1/voyages/V004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voyageNumber").value("V004"));
    }

    @Test
    @DisplayName("GET /api/routing/v1/voyages/{voyageNumber} — 存在しない場合は 404 を返すこと")
    void shouldReturn404WhenVoyageNotFound() throws Exception {
        mockMvc.perform(get("/api/routing/v1/voyages/NOTEXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/routing/v1/voyages/{voyageNumber} — スケジュールを更新できること")
    void shouldUpdateVoyageSchedule() throws Exception {
        mockMvc.perform(post("/api/routing/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("V005")))
                .andExpect(status().isCreated());

        String updateBody = """
                {"carrierMovements":[
                  {"departureLocationUnlocode":"JPOSA","arrivalLocationUnlocode":"KRPUS",
                   "departureDate":"2025-01-10T08:00:00+09:00","arrivalDate":"2025-01-12T18:00:00+09:00","seqNumber":0}
                ]}
                """;

        mockMvc.perform(put("/api/routing/v1/voyages/V005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrierMovements[0].departureLocationUnlocode").value("JPOSA"))
                .andExpect(jsonPath("$.carrierMovements[0].arrivalLocationUnlocode").value("KRPUS"));
    }

    @Test
    @DisplayName("DELETE /api/routing/v1/voyages/{voyageNumber} — 航海を削除できること")
    void shouldDeleteVoyage() throws Exception {
        mockMvc.perform(post("/api/routing/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("V006")))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/routing/v1/voyages/V006"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/routing/v1/voyages/V006"))
                .andExpect(status().isNotFound());
    }
}
