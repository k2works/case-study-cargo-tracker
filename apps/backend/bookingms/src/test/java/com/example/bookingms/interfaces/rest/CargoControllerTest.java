package com.example.bookingms.interfaces.rest;

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
 * CargoController 統合テスト（H2 インメモリ DB 使用）
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("CargoController 統合テスト")
class CargoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String CREATE_CARGO_BODY = """
            {
              "shipperId": 1,
              "cargoType": "GENERAL",
              "weightKg": 100.0,
              "originUnlocode": "JPTYO",
              "destinationUnlocode": "CNSHA",
              "arrivalDeadline": "2026-06-30"
            }
            """;

    @Test
    @DisplayName("POST /api/booking/v1/cargos — 貨物を正常に登録できること")
    void shouldCreateCargo() throws Exception {
        mockMvc.perform(post("/api/booking/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CARGO_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNotEmpty())
                .andExpect(jsonPath("$.bookingStatus").value("PRELIMINARY"))
                .andExpect(jsonPath("$.cargoType").value("GENERAL"))
                .andExpect(jsonPath("$.weightKg").value(100.0))
                .andExpect(jsonPath("$.originUnlocode").value("JPTYO"))
                .andExpect(jsonPath("$.destinationUnlocode").value("CNSHA"));
    }

    @Test
    @DisplayName("GET /api/booking/v1/cargos — 全貨物一覧を取得できること")
    void shouldListCargos() throws Exception {
        mockMvc.perform(post("/api/booking/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CARGO_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/booking/v1/cargos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].bookingStatus").value("PRELIMINARY"));
    }

    @Test
    @DisplayName("GET /api/booking/v1/cargos/{bookingId} — 予約 ID で貨物を取得できること")
    void shouldGetCargoByBookingId() throws Exception {
        String responseBody = mockMvc.perform(post("/api/booking/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CARGO_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract bookingId from response
        String bookingId = responseBody.replaceAll(".*\"bookingId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/booking/v1/cargos/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId));
    }

    @Test
    @DisplayName("GET /api/booking/v1/cargos/{bookingId} — 存在しない場合は 404 を返すこと")
    void shouldReturn404WhenCargoNotFound() throws Exception {
        mockMvc.perform(get("/api/booking/v1/cargos/NOTEXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/booking/v1/cargos/{bookingId}/route — 経路を割り当てると ROUTE_PROPOSED になること")
    void shouldAssignRoute() throws Exception {
        // 貨物を登録
        String responseBody = mockMvc.perform(post("/api/booking/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CARGO_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String bookingId = responseBody.replaceAll(".*\"bookingId\":\"([^\"]+)\".*", "$1");

        // 経路を割り当てる
        String assignRouteBody = """
                {
                  "legs": [
                    {
                      "voyageNumber": "V0042",
                      "loadLocationUnlocode": "JPTYO",
                      "unloadLocationUnlocode": "CNSHA",
                      "loadTime": "2026-04-01T18:00:00",
                      "unloadTime": "2026-04-14T08:00:00"
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/booking/v1/cargos/" + bookingId + "/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignRouteBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.bookingStatus").value("ROUTE_PROPOSED"));
    }

    @Test
    @DisplayName("PUT /api/booking/v1/cargos/{bookingId}/route — 存在しない貨物の場合は 404 を返すこと")
    void shouldReturn404WhenAssigningRouteToNonExistentCargo() throws Exception {
        String assignRouteBody = """
                {
                  "legs": [
                    {
                      "voyageNumber": "V0042",
                      "loadLocationUnlocode": "JPTYO",
                      "unloadLocationUnlocode": "CNSHA",
                      "loadTime": "2026-04-01T18:00:00",
                      "unloadTime": "2026-04-14T08:00:00"
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/booking/v1/cargos/NOTEXIST/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignRouteBody))
                .andExpect(status().isNotFound());
    }
}
