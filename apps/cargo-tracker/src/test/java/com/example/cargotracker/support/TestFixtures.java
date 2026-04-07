package com.example.cargotracker.support;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E テスト・統合テスト共通のデータ登録ヘルパー。
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    /**
     * テスト用荷主を登録し、生成された荷主 ID を返す。
     */
    public static String registerShipper(MockMvc mockMvc, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/shippers")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "E2E 荷主",
                                  "email": "e2e-shipper@example.com",
                                  "phone": "090-1111-2222",
                                  "shipperType": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        return result.getResponse().getContentAsString()
                .replaceFirst("^.*\"id\"\\s*:\\s*\"([^\"]+)\".*$", "$1");
    }

    /**
     * テスト用貨物予約を登録し、生成された予約 ID を返す。
     */
    public static String registerBooking(MockMvc mockMvc, MockHttpSession session, String shipperId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shipperId": "%s",
                                  "cargoType": "GENERAL",
                                  "weight": 12.750,
                                  "originUnlocode": "JPTYO",
                                  "destinationUnlocode": "NLRTM",
                                  "arrivalDeadline": "%s"
                                }
                                """.formatted(shipperId, LocalDate.now().plusDays(21))))
                .andExpect(status().isCreated())
                .andReturn();

        return result.getResponse().getContentAsString()
                .replaceFirst("^.*\"bookingId\"\\s*:\\s*\"([^\"]+)\".*$", "$1");
    }
}
