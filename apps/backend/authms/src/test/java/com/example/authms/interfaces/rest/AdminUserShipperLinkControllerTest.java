package com.example.authms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.authms.application.internal.commandservices.ManageUserShipperLinkUseCase;
import com.example.authms.domain.model.UserShipperLink;
import com.example.shared.auth.AuthenticatedUser;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserShipperLinkController.class)
@DisplayName("利用者と荷主の紐付け管理 API")
class AdminUserShipperLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageUserShipperLinkUseCase links;

    @MockitoBean
    private com.example.authms.application.internal.queryservices.FindUserShipperLinkUseCase find;

    /**
     * <strong>付け替える前に、いまの相手を確かめられる。</strong>見えないと、
     * 管理者は自分が何を壊すのか分からないまま上書きすることになる。
     */
    @Test
    @DisplayName("管理者は、その利用者がいま誰に紐付いているかを読める")
    void showsCurrentLink() throws Exception {
        when(find.find("shipper01")).thenReturn(
                com.example.authms.application.internal.queryservices.UserShipperLinkResult
                        .linked(9001L));

        mockMvc.perform(get("/api/v1/admin/user-shipper-links/shipper01")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("shipper01"))
                .andExpect(jsonPath("$.shipperId").value(9001));
    }

    /**
     * <strong>「利用者が居ない」と「紐付いていない」を取り違えさせない。</strong>
     * 404 にすると、呼び出し側は名前の綴り違いと区別できない。
     */
    @Test
    @DisplayName("紐付いていない利用者は、200 で空の荷主 ID を返す")
    void showsUnlinkedUser() throws Exception {
        when(find.find("shipper02")).thenReturn(
                com.example.authms.application.internal.queryservices.UserShipperLinkResult
                        .unlinked());

        mockMvc.perform(get("/api/v1/admin/user-shipper-links/shipper02")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipperId").doesNotExist());
    }

    @Test
    @DisplayName("管理者以外は、紐付けを読めない")
    void othersCannotRead() throws Exception {
        mockMvc.perform(get("/api/v1/admin/user-shipper-links/shipper01")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理者は利用者を荷主に紐付けられる")
    void linksUserToShipper() throws Exception {
        when(links.link("shipper01", 1L))
                .thenReturn(Optional.of(new UserShipperLink("shipper01", 1L)));

        mockMvc.perform(put("/api/v1/admin/user-shipper-links/shipper01")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipperId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("shipper01"))
                .andExpect(jsonPath("$.shipperId").value(1));
    }

    @Test
    @DisplayName("管理者は紐付けを解除できる")
    void unlinksUser() throws Exception {
        when(links.unlink("shipper01"))
                .thenReturn(Optional.of(new UserShipperLink("shipper01", 1L)));

        mockMvc.perform(delete("/api/v1/admin/user-shipper-links/shipper01")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("shipper01"))
                .andExpect(jsonPath("$.shipperId").value(1));
    }

    @Test
    @DisplayName("管理者以外は紐付けできない")
    void rejectsNonAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/user-shipper-links/shipper01")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipperId\": 1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/user-shipper-links/shipper01")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isForbidden());

        verify(links, never()).link(any(), any());
        verify(links, never()).unlink(any());
    }

    @Test
    @DisplayName("存在しない利用者は 404")
    void returns404WhenUserMissing() throws Exception {
        when(links.link("missing", 1L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/admin/user-shipper-links/missing")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipperId\": 1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("荷主 ID が不正なら 400")
    void rejectsInvalidShipperId() throws Exception {
        mockMvc.perform(put("/api/v1/admin/user-shipper-links/shipper01")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipperId\": 0}"))
                .andExpect(status().isBadRequest());
    }
}
