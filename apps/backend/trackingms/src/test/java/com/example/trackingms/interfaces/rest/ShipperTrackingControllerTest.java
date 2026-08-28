package com.example.trackingms.interfaces.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.auth.AuthenticatedUser;
import com.example.trackingms.application.internal.queryservices.ShipperTrackingDetail;
import com.example.trackingms.application.internal.queryservices.ShipperTrackingEvent;
import com.example.trackingms.application.internal.queryservices.ShipperTrackingQueryResult;
import com.example.trackingms.application.internal.queryservices.ShipperTrackingQueryUseCase;
import com.example.trackingms.application.internal.queryservices.ShipperTrackingSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ShipperTrackingController.class)
@DisplayName("荷主向け追跡 API")
class ShipperTrackingControllerTest {

    private static final String NUMBER = "TRK-20260823-0001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipperTrackingQueryUseCase query;

    @Test
    @DisplayName("荷主は自社貨物一覧を取得できる")
    void returnsOwnCargoList() throws Exception {
        when(query.list("shipper01")).thenReturn(ShipperTrackingQueryResult.linked(List.of(
                new ShipperTrackingSummary(NUMBER, "RECEIVED", "受領済み", "Tokyo",
                        LocalDate.of(2027, 9, 15), false, false))));

        mockMvc.perform(get("/api/v1/shipper/tracking")
                        .header(AuthenticatedUser.USER_ID_HEADER, "shipper01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SHIPPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.cargos[0].trackingNumber").value(NUMBER))
                .andExpect(jsonPath("$.cargos[0].statusLabel").value("受領済み"))
                .andExpect(jsonPath("$.cargos[0].estimatedArrival").value("2027-09-15"));
    }

    @Test
    @DisplayName("荷主に紐付いていないときは案内文を返す")
    void returnsUnlinkedMessage() throws Exception {
        when(query.list("shipper01")).thenReturn(ShipperTrackingQueryResult.unlinked());

        mockMvc.perform(get("/api/v1/shipper/tracking")
                        .header(AuthenticatedUser.USER_ID_HEADER, "shipper01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SHIPPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.contactMessage").value(
                        org.hamcrest.Matchers.containsString("営業担当")))
                .andExpect(jsonPath("$.cargos").isEmpty());
    }

    @Test
    @DisplayName("荷主は自社貨物の詳細と経過を取得できる")
    void returnsOwnCargoDetail() throws Exception {
        when(query.detail("shipper01", NUMBER)).thenReturn(Optional.of(
                new ShipperTrackingDetail(NUMBER, "RECEIVED", "受領済み", "Tokyo",
                        LocalDate.of(2027, 9, 15), true, true,
                        List.of(new ShipperTrackingEvent("2027-09-02 09:00", "RECEIVED",
                                "受領済み", "Tokyo")))));

        mockMvc.perform(get("/api/v1/shipper/tracking/" + NUMBER)
                        .header(AuthenticatedUser.USER_ID_HEADER, "shipper01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SHIPPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasException").value(true))
                .andExpect(jsonPath("$.urgent").value(true))
                .andExpect(jsonPath("$.events[0].statusLabel").value("受領済み"));
    }

    @Test
    @DisplayName("自社貨物でなければ 404 にする")
    void returnsNotFoundForOtherShippersCargo() throws Exception {
        when(query.detail("shipper01", NUMBER)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/shipper/tracking/" + NUMBER)
                        .header(AuthenticatedUser.USER_ID_HEADER, "shipper01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SHIPPER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("自社の貨物として確認できません"));
    }

    @Test
    @DisplayName("荷主以外には開かない")
    void rejectsNonShipper() throws Exception {
        mockMvc.perform(get("/api/v1/shipper/tracking")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isForbidden());
    }
}
