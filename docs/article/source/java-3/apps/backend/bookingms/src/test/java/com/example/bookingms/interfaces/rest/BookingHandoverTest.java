package com.example.bookingms.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookingms.application.internal.commandservices.ConfirmBookingUseCase;
import com.example.bookingms.application.internal.commandservices.NotifyShipperUseCase;
import com.example.bookingms.application.internal.commandservices.RequestRoutingUseCase;
import com.example.bookingms.application.internal.queryservices.SearchCargoUseCase;
import com.example.bookingms.application.internal.commandservices.ReturnToRoutingUseCase;
import com.example.bookingms.application.internal.commandservices.ReviseBookingScheduleUseCase;
import com.example.bookingms.domain.repository.CargoRepository;
import com.example.bookingms.domain.repository.CargoSummary;
import com.example.bookingms.domain.repository.LocationRepository;
import com.example.bookingms.domain.model.valueobjects.RoutingStatus;
import com.example.shared.auth.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 予約が手番を渡していく道筋（US06・US12〜US14・[ADR-021]）。
 *
 * <p>予約を作る・見る（{@link CargoBookingControllerTest}）と分けたのは、確かめている
 * ものが違うからである。ここで見るのは<strong>手番の受け渡し</strong>——引き渡し・通知・
 * 確定・差し戻し・訂正であり、いずれも状態が誰の手にあるかを動かす。
 *
 * <p><strong>判定は集約が持つ</strong>。ここで確かめるのは、入口が正しい相手に頼み、
 * 断られたときに正しい形で返すことである。
 */
@WebMvcTest(CargoBookingController.class)
@DisplayName("予約の手番 API")
class BookingHandoverTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestRoutingUseCase requestRouting;

    @MockitoBean
    private NotifyShipperUseCase notifyShipper;

    @MockitoBean
    private ConfirmBookingUseCase confirmBooking;

    @MockitoBean
    private ReturnToRoutingUseCase returnToRouting;

    @MockitoBean
    private ReviseBookingScheduleUseCase reviseSchedule;

    @MockitoBean
    private BookingUseCases useCases;

    @MockitoBean
    private CargoRepository cargoes;

    @MockitoBean
    private SearchCargoUseCase searchCargo;

    @MockitoBean
    private LocationRepository locations;

    @BeforeEach
    void wireUseCases() {
        when(useCases.requestRouting()).thenReturn(requestRouting);
        when(useCases.searchCargo()).thenReturn(searchCargo);
        when(useCases.notifyShipper()).thenReturn(notifyShipper);
        when(useCases.confirmBooking()).thenReturn(confirmBooking);
        when(useCases.returnToRouting()).thenReturn(returnToRouting);
        when(useCases.reviseSchedule()).thenReturn(reviseSchedule);
    }


    @Test
    @DisplayName("営業担当者は経路設計を依頼できる")
    void salesRequestsRouting() throws Exception {
        when(requestRouting.request("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.booked().requestRouting(), "丸紅商事", "SHP-0001")));

        mockMvc.perform(post("/api/v1/bookings/BKG-2026000001/routing-request")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routingStatus").value("ROUTING_REQUESTED"));
    }

    /**
     * 経路設計者が自分で依頼を立てられない。
     *
     * <p>立てられると、引き渡しの記録が「誰が渡したか」を表さなくなる。
     */
    @Test
    @DisplayName("経路設計者は依頼できない")
    void routingPlannerCannotRequest() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/BKG-2026000001/routing-request")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isForbidden());

        verify(requestRouting, never()).request(any());
    }

    /**
     * 依頼できない状態は 409 で返す。
     *
     * <p>入力の誤り（400）ではない。400 で返すと、画面は「入力を直してください」と伝える
     * ことになるが、直すべき入力は無い。
     */
    @Test
    @DisplayName("依頼済みの予約への再依頼は 409")
    void secondRequestIsConflict() throws Exception {
        when(requestRouting.request("BKG-2026000001"))
                .thenThrow(new IllegalStateException("この予約はすでに経路設計を依頼しています"));

        mockMvc.perform(post("/api/v1/bookings/BKG-2026000001/routing-request")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("この予約はすでに経路設計を依頼しています"));
    }

    @Test
    @DisplayName("存在しない予約への依頼は 404")
    void unknownBookingIsNotFound() throws Exception {
        when(requestRouting.request("BKG-9999999999")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/bookings/BKG-9999999999/routing-request")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("引き渡された予約の詳細は営業担当者も経路設計者も見られる")
    void bothRolesSeeDetail() throws Exception {
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.requested(), "丸紅商事", "SHP-0001")));

        for (String role : List.of("ROLE_SALES", "ROLE_ROUTING")) {
            mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value("BKG-2026000001"))
                    .andExpect(jsonPath("$.shipperName").value("丸紅商事"));
        }
    }

    /**
     * 一覧の制限を、予約番号の列挙で迂回できてはいけない。
     *
     * <p>一覧は依頼済みに絞られているが、詳細が絞られていなければ、経路設計者は
     * 予約番号を順に試すだけで営業が作業中の予約をすべて読める。**入口を 1 つ塞いでも、
     * 同じ範囲を返すもう 1 つの入口が開いていれば、絞りは無いのと同じ**。
     */
    @Test
    @DisplayName("経路設計者は、まだ引き渡されていない予約の詳細を見られない")
    void routingPlannerCannotOpenUnrequestedDetail() throws Exception {
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.booked(), "丸紅商事", "SHP-0001")));

        mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isNotFound());
    }

    /**
     * 見えない予約と存在しない予約を、応答で区別しない（残作業 11）。
     *
     * <p>403 と 404 を打ち分けると、予約番号を順に試すだけで<strong>どの番号が
     * 実在するか</strong>が分かる。内容は隠れても、営業がいま何件抱えているかは漏れる。
     * 番号は連番であり、総当たりは容易である。
     */
    @Test
    @DisplayName("見えない予約と存在しない予約は、応答で区別できない")
    void invisibleAndUnknownAreIndistinguishable() throws Exception {
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.booked(), "丸紅商事", "SHP-0001")));
        when(cargoes.findByBookingId("BKG-9999999999")).thenReturn(Optional.empty());

        String invisible = mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(get("/api/v1/bookings/BKG-9999999999")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andReturn().getResponse().getContentAsString();

        // 状態だけでなく本文も同じにする。文言が違えば、そこから存在が読める
        assertThat(invisible).isEqualTo(unknown);
    }

    /**
     * 決定 3（[ADR-020]）の API 側の検査。
     *
     * <p>割り当てた直後に自分が開けなくなると、確定画面にも旅程にも辿り着けない。
     * 集約の述語だけを確かめても、入口が別の判断を書いていれば意味がない。
     */
    @Test
    @DisplayName("経路が決まった予約も、経路設計者が開ける")
    void routingPlannerCanOpenRoutedDetail() throws Exception {
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.routed(), "丸紅商事", "SHP-0001")));

        mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isOk());
    }

    /** 営業担当者を兼ねる利用者は、営業として見られる。 */
    @Test
    @DisplayName("営業担当者は引き渡し前の予約の詳細を見られる")
    void salesSeesUnrequestedDetail() throws Exception {
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.booked(), "丸紅商事", "SHP-0001")));

        mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("経路が決まっていない予約は旅程を返さない（空の配列にしない）")
    void unroutedCargoHasNoItinerary() throws Exception {
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.booked(), "丸紅商事", "SHP-0001")));

        // 空の配列にすると「区間が 0 件の旅程がある」と読め、画面が空の表を出す
        mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itinerary").doesNotExist());
    }

    @Test
    @DisplayName("存在しない予約の詳細は 404")
    void unknownDetailIsNotFound() throws Exception {
        when(cargoes.findByBookingId(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/bookings/BKG-9999999999")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isNotFound());
    }

    /**
     * 経路設計者に見せる範囲は、引き渡された予約に限る。
     *
     * <p>US06 のために一覧を開くが、全件を開くわけではない。まだ引き渡されていない予約
     * （営業が作業中のもの）は、経路設計者の仕事の対象ではない。
     */
    @Test
    @DisplayName("経路設計者の一覧は依頼済みだけに絞られる")
    void routingPlannerSeesOnlyRequested() throws Exception {
        when(searchCargo.search(any(), any(), any(), any()))
                .thenReturn(new SearchCargoUseCase.Result(List.of(), 0L, 100));

        mockMvc.perform(get("/api/v1/bookings")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isOk());

        // 範囲は集約の判定から導く。一覧が詳細より狭いと、割り当てた予約も差し戻した
        // 予約も一覧から消え、経路設計者は自分が触った予約を見失う（IT5 レビュー 高 4）
        verify(searchCargo).search(null, null, RoutingStatus.openToRoutingPlanner(), null);
        assertThat(RoutingStatus.openToRoutingPlanner())
                .as("引き渡されていない予約は開かない")
                .doesNotContain(RoutingStatus.NOT_ROUTED)
                .contains(RoutingStatus.ROUTING_REQUESTED, RoutingStatus.ROUTED,
                        RoutingStatus.CONSULTATION_REQUESTED);
    }

    @Test
    @DisplayName("経路設計者が開いてよい状態で絞り込むと、その状態だけになる")
    void routingPlannerCanNarrowWithinTheOpenRange() throws Exception {
        when(searchCargo.search(any(), any(), any(), any()))
                .thenReturn(new SearchCargoUseCase.Result(List.of(), 0L, 100));

        mockMvc.perform(get("/api/v1/bookings")
                        .param("routingStatus", "ROUTING_REQUESTED")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isOk());

        // 待ち行列だけを見る使い方は残す（US06 の導線）
        verify(searchCargo).search(null, null, List.of(RoutingStatus.ROUTING_REQUESTED), null);
    }

    /** 絞り込みを外そうとしても、経路設計者には効かない。 */
    @Test
    @DisplayName("経路設計者が条件を外しても全件は見えない")
    void routingPlannerCannotWidenTheList() throws Exception {
        when(searchCargo.search(any(), any(), any(), any()))
                .thenReturn(new SearchCargoUseCase.Result(List.of(), 0L, 100));

        mockMvc.perform(get("/api/v1/bookings")
                        .param("routingStatus", "NOT_ROUTED")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isOk());

        // 指定で範囲は広げられない。開いてよい範囲へ落とす
        verify(searchCargo).search(null, null, RoutingStatus.openToRoutingPlanner(), null);
    }
}
