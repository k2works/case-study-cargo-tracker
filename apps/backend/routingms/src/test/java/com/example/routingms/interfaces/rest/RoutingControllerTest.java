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
 * RoutingController 統合テスト（H2 インメモリ DB 使用）
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("RoutingController 統合テスト")
class RoutingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/routing/v1/itineraries — 直行便が存在する場合に旅程候補を返すこと")
    void shouldReturnItinerariesWhenDirectVoyageExists() throws Exception {
        // 航海を登録
        mockMvc.perform(post("/api/routing/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "voyageNumber": "V-R100",
                                  "carrierMovements": [
                                    {
                                      "departureLocationUnlocode": "JPTYO",
                                      "arrivalLocationUnlocode": "CNSHA",
                                      "departureDate": "2026-07-01T08:00:00+09:00",
                                      "arrivalDate": "2026-07-03T18:00:00+09:00",
                                      "seqNumber": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        // 経路候補算出
        mockMvc.perform(post("/api/routing/v1/itineraries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originUnlocode": "JPTYO",
                                  "destinationUnlocode": "CNSHA",
                                  "arrivalDeadline": "2026-07-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].legs").isArray())
                .andExpect(jsonPath("$[0].legs[0].voyageNumber").value("V-R100"))
                .andExpect(jsonPath("$[0].legs[0].loadLocationUnlocode").value("JPTYO"))
                .andExpect(jsonPath("$[0].legs[0].unloadLocationUnlocode").value("CNSHA"));
    }

    @Test
    @DisplayName("POST /api/routing/v1/itineraries — 期限を超える航海は除外されること")
    void shouldExcludeVoyagesAfterDeadline() throws Exception {
        mockMvc.perform(post("/api/routing/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "voyageNumber": "V-R101",
                                  "carrierMovements": [
                                    {
                                      "departureLocationUnlocode": "JPTYO",
                                      "arrivalLocationUnlocode": "CNSHA",
                                      "departureDate": "2026-07-01T08:00:00+09:00",
                                      "arrivalDate": "2026-07-03T18:00:00+09:00",
                                      "seqNumber": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        // 期限が到着日より前の場合
        mockMvc.perform(post("/api/routing/v1/itineraries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originUnlocode": "JPTYO",
                                  "destinationUnlocode": "CNSHA",
                                  "arrivalDeadline": "2026-07-02"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("POST /api/routing/v1/itineraries — 対象路線がない場合は空リストを返すこと")
    void shouldReturnEmptyListWhenNoRouteFound() throws Exception {
        mockMvc.perform(post("/api/routing/v1/itineraries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originUnlocode": "JPTYO",
                                  "destinationUnlocode": "USNYC",
                                  "arrivalDeadline": "2026-12-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
