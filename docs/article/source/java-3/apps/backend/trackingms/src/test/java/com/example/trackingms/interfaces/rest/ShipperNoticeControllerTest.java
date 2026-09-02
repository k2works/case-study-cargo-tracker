package com.example.trackingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.auth.AuthenticatedUser;
import com.example.trackingms.application.internal.queryservices.ShipperNoticeQueryUseCase;
import com.example.trackingms.domain.model.valueobjects.ShipperNotice;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ShipperNoticeController.class)
@DisplayName("荷主向けお知らせ API")
class ShipperNoticeControllerTest {

    private static final String NUMBER = "TRK-20260823-0001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipperNoticeQueryUseCase query;

    @Test
    @DisplayName("まだ見ていない知らせを、古い順に返す")
    void returnsUnread() throws Exception {
        when(query.unread("shipper01")).thenReturn(List.of(
                new ShipperNotice(1L, TrackingNumber.of(NUMBER),
                        Instant.parse("2026-09-01T00:00:00Z"), "積み込みました"),
                new ShipperNotice(2L, TrackingNumber.of(NUMBER),
                        Instant.parse("2026-09-01T01:00:00Z"), "出港しました")));

        mockMvc.perform(get("/api/v1/shipper/notifications")
                        .header(AuthenticatedUser.USER_ID_HEADER, "shipper01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SHIPPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].id").value(1))
                .andExpect(jsonPath("$.notifications[0].message").value("積み込みました"))
                .andExpect(jsonPath("$.notifications[0].trackingNumber").value(NUMBER))
                .andExpect(jsonPath("$.notifications[1].id").value(2));
    }

    @Test
    @DisplayName("知らせが無ければ、空の配列を返す")
    void returnsEmptyArray() throws Exception {
        when(query.unread("shipper01")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/shipper/notifications")
                        .header(AuthenticatedUser.USER_ID_HEADER, "shipper01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SHIPPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isArray())
                .andExpect(jsonPath("$.notifications").isEmpty());
    }

    /**
     * <strong>認可は入力検証より先に置く</strong>（IT13 の学び）。荷主以外は、
     * 何を送っても同じく断られる。
     */
    @Test
    @DisplayName("荷主以外は読めない")
    void othersAreForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/shipper/notifications")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isForbidden());

        verify(query, never()).unread(anyString());
    }

    @Test
    @DisplayName("読んだ位置を進められる")
    void acknowledges() throws Exception {
        mockMvc.perform(post("/api/v1/shipper/notifications/read")
                        .header(AuthenticatedUser.USER_ID_HEADER, "shipper01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SHIPPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastNoticeId\":2}"))
                .andExpect(status().isNoContent());

        verify(query).acknowledge("shipper01", 2L);
    }

    @Test
    @DisplayName("荷主以外は読んだことにできない")
    void othersCannotAcknowledge() throws Exception {
        mockMvc.perform(post("/api/v1/shipper/notifications/read")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastNoticeId\":2}"))
                .andExpect(status().isForbidden());

        verify(query, never()).acknowledge(anyString(), ArgumentMatchers.anyLong());
    }
}
