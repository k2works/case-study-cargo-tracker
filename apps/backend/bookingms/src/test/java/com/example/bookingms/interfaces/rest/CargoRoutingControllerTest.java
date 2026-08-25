package com.example.bookingms.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookingms.application.internal.AssignRouteUseCase;
import com.example.bookingms.application.internal.IssueTrackingNumberUseCase;
import com.example.bookingms.application.internal.LocationMasterMissingException;
import com.example.bookingms.application.internal.RequestConsultationUseCase;
import com.example.bookingms.application.internal.RouteNoLongerAvailableException;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 経路設計者の手番の入口（[ADR-008] の職掌分離）。
 *
 * <p>営業の手番（{@link CargoBookingControllerTest}）と分けたのは、入口を分けたからである。
 * 貨物の組み立ては {@link BookingTestCargoes} で共有する——写すと片方だけ直る。
 */
@WebMvcTest(CargoRoutingController.class)
@DisplayName("経路設計 API")
class CargoRoutingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssignRouteUseCase assignRoute;

    @MockitoBean
    private RequestConsultationUseCase requestConsultation;

    @MockitoBean
    private IssueTrackingNumberUseCase issueTrackingNumber;

    @MockitoBean
    private BookingUseCases useCases;

    @MockitoBean
    private LocationRepository locations;

    @BeforeEach
    void wireUseCases() {
        when(useCases.assignRoute()).thenReturn(assignRoute);
        when(useCases.requestConsultation()).thenReturn(requestConsultation);
        when(useCases.issueTrackingNumber()).thenReturn(issueTrackingNumber);
    }


    private static final String ROUTE_BODY = """
            {"legs": [
              {"voyageNumber": "V0100", "loadUnLocode": "JPTYO", "unloadUnLocode": "USLAX",
               "loadTime": "2027-09-02T09:00:00Z", "unloadTime": "2027-09-15T09:00:00Z"}
            ], "maxTransshipments": 2}
            """;

    /** 解析はできるが、地点が実在しない本文。 */
    private static final String UNKNOWN_PORT_BODY = """
            {"legs": [
              {"voyageNumber": "V0100", "loadUnLocode": "XXXXX", "unloadUnLocode": "USLAX",
               "loadTime": "2027-09-02T09:00:00Z", "unloadTime": "2027-09-15T09:00:00Z"}
            ], "maxTransshipments": 2}
            """;

    /** 積込地と荷降し地は別の地点を返す。同じにすると区間そのものが成り立たない */
    private void givenKnownPorts() {
        when(locations.findByUnLocode("JPTYO"))
                .thenReturn(Optional.of(Location.of("JPTYO", "Tokyo")));
        when(locations.findByUnLocode("USLAX"))
                .thenReturn(Optional.of(Location.of("USLAX", "Los Angeles")));
    }

    @Test
    @DisplayName("経路設計者が割り当てると 200 と割り当て後の予約を返す")
    void assigns() throws Exception {
        givenKnownPorts();
        when(assignRoute.assign(any(), any(), any())).thenReturn(Optional.of(
                new com.example.bookingms.application.internal.AssignRouteUseCase.AssignmentResult(
                        BookingTestCargoes.routed(), null)));

        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/route")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROUTE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routingStatus").value("ROUTED"))
                .andExpect(jsonPath("$.bookingStatus").value("ROUTE_PROPOSED"))
                // 旅程を返さないと、確定した直後の画面に経路が出ない
                .andExpect(jsonPath("$.itinerary[0].voyageNumber").value("V0100"))
                // 港は名前まで返す。画面が 5 文字のコードから引き直さずに済む
                .andExpect(jsonPath("$.itinerary[0].loadName").value("Tokyo"))
                .andExpect(jsonPath("$.itinerary[0].unloadName").value("Los Angeles"));

        // 地点はマスタから引く。画面が送った名称を信じると、地点名の直しが 2 か所に分かれる
        ArgumentCaptor<CargoItinerary> captor = ArgumentCaptor.forClass(CargoItinerary.class);
        verify(assignRoute).assign(org.mockito.ArgumentMatchers.eq("BKG-2026000001"),
                captor.capture(), org.mockito.ArgumentMatchers.eq(2));
        assertThat(captor.getValue().origin().name()).isEqualTo("Tokyo");
    }

    /** ADR-019 決定 2。もう出ない便の旅程が予約に入らないようにする。 */
    @Test
    @DisplayName("選んだ経路がもう成立しなければ 409")
    void reportsConflictWhenNoLongerAvailable() throws Exception {
        givenKnownPorts();
        when(assignRoute.assign(any(), any(), any()))
                .thenThrow(new RouteNoLongerAvailableException("選んだ経路はもう使えません"));

        // 入力の誤り（400）ではない。直すべきは入力ではなく、経路をもう一度探すこと
        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/route")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROUTE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("もう使えません")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ROLE_SALES", "ROLE_SHIPPER", "ROLE_HANDLER", "ROLE_TRACKER",
        "ROLE_ACCOUNTANT", "ROLE_ADMIN"
    })
    @DisplayName("経路設計者以外は 403 で拒否し、ユースケースを呼ばない")
    void rejectsOtherRoles(String role) throws Exception {
        // 営業が自分で経路を確定できると、職掌分離（ADR-008）が崩れる
        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/route")
                        .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                        .header(AuthenticatedUser.ROLES_HEADER, role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROUTE_BODY))
                .andExpect(status().isForbidden());

        verify(assignRoute, never()).assign(any(), any(), any());
    }

    /**
     * ADR-016 の回帰。認可を入力の検査より先に置く。
     *
     * <p>本文は<strong>解析はできるが検証に落ちる</strong>ものを使う。解析できない本文は
     * フレームワークが引数を組み立てる前に断るため、認可を先に置いても 400 になる。
     */
    @Test
    @DisplayName("本文が不正でも、権限が無ければ 403")
    void checksPermissionBeforeValidation() throws Exception {
        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/route")
                        .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNKNOWN_PORT_BODY))
                .andExpect(status().isForbidden());

        verify(assignRoute, never()).assign(any(), any(), any());
    }

    @Test
    @DisplayName("実在しない地点は 400 で理由を返す")
    void rejectsUnknownPort() throws Exception {
        when(locations.findByUnLocode("XXXXX")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/route")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNKNOWN_PORT_BODY))
                .andExpect(status().isBadRequest());

        verify(assignRoute, never()).assign(any(), any(), any());
    }

    @Test
    @DisplayName("区間が空なら 400")
    void rejectsEmptyLegs() throws Exception {
        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/route")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legs\": []}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * <strong>409 の射程を絞る</strong>（IT6 タスク 0.4）。
     *
     * <p>地点マスタに目的地が無いのは<strong>こちら側の不備</strong>であり、経路設計者が
     * 何度探し直しても直らない。409 と「経路をもう一度探してください」で返すと、
     * 直せない作業をさせたうえ、原因が記録に残らない。
     */
    @Test
    @DisplayName("こちら側の不備（地点マスタの欠落）は 409 にしない")
    void doesNotReportOurOwnDefectAsConflict() throws Exception {
        givenKnownPorts();
        when(assignRoute.assign(any(), any(), any()))
                .thenThrow(new LocationMasterMissingException("USLAX"));

        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/route")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROUTE_BODY))
                .andExpect(status().isInternalServerError())
                // 直らない作業を促さない
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("探して"))))
                // どの地点が無いかは返さない（利用者に使い道が無く、構成を漏らすだけ）
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("USLAX"))));
    }

    @Test
    @DisplayName("見つからない予約への割り当ては 404")
    void reportsMissingBooking() throws Exception {
        givenKnownPorts();
        when(assignRoute.assign(any(), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/bookings/BKG-9999999999/route")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROUTE_BODY))
                .andExpect(status().isNotFound());
    }
    @Test
    @DisplayName("経路設計者が追跡番号を発行でき、番号が応答に載る")
    void issuesTrackingNumber() throws Exception {
        when(issueTrackingNumber.issue(any())).thenReturn(Optional.of(BookingTestCargoes.notified().confirm()
                .issueTrackingNumber(com.example.bookingms.domain.model.TrackingNumber
                        .of("TRK-20260822-0001"))));

        mockMvc.perform(post("/api/v1/bookings/BKG-2026000001/tracking-number")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("TRACKING_ISSUED"))
                .andExpect(jsonPath("$.trackingNumber").value("TRK-20260822-0001"))
                // 貨物はまだ動いていない（US14-3）
                .andExpect(jsonPath("$.transportStatus").value("NOT_RECEIVED"));
    }

    @Test
    @DisplayName("営業は追跡番号を発行できない（403）")
    void salesCannotIssueTrackingNumber() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/BKG-2026000001/tracking-number")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isForbidden());

        verify(issueTrackingNumber, never()).issue(any());
    }
}
