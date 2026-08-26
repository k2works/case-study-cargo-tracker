package com.example.bookingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookingms.application.internal.ConfirmBookingUseCase;
import com.example.bookingms.application.internal.NotifyShipperUseCase;
import com.example.bookingms.application.internal.ReturnToRoutingUseCase;
import com.example.bookingms.application.internal.ReviseBookingScheduleUseCase;
import com.example.bookingms.application.internal.SearchCargoUseCase;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.VoyageNumber;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 荷主への通知・確定・差し戻し・訂正（US12・US13・[ADR-021]）。
 *
 * <p>引き渡しと見え方（{@link BookingHandoverTest}）と分けたのは、確かめているものが
 * 違うからである。ここで見るのは<strong>荷主との合意で予約が固まっていく道筋</strong>で、
 * 固まったあとに何が変えられなくなるかまでを含む。
 *
 * <p><strong>判定は集約が持つ</strong>。ここで確かめるのは、入口が正しい相手に頼み、
 * 断られたときに正しい形で返すことである。
 */
@WebMvcTest(CargoBookingController.class)
@DisplayName("予約の確定 API")
class BookingConfirmationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotifyShipperUseCase notifyShipper;

    @MockitoBean
    private ConfirmBookingUseCase confirmBooking;

    @MockitoBean
    private ReturnToRoutingUseCase returnToRouting;

    @MockitoBean
    private ReviseBookingScheduleUseCase reviseSchedule;

    @MockitoBean
    private SearchCargoUseCase searchCargo;

    @MockitoBean
    private BookingUseCases useCases;

    @MockitoBean
    private CargoRepository cargoes;

    @MockitoBean
    private LocationRepository locations;

    @BeforeEach
    void wireUseCases() {
        when(useCases.notifyShipper()).thenReturn(notifyShipper);
        when(useCases.confirmBooking()).thenReturn(confirmBooking);
        when(useCases.returnToRouting()).thenReturn(returnToRouting);
        when(useCases.reviseSchedule()).thenReturn(reviseSchedule);
        when(useCases.searchCargo()).thenReturn(searchCargo);
    }

    @Test
    @DisplayName("営業が荷主へ通知でき、いつ・誰が が応答に載る")
    void notifiesShipper() throws Exception {
        when(notifyShipper.notifyShipper(any(), any())).thenReturn(Optional.of(BookingTestCargoes.notified()));

        mockMvc.perform(post("/api/v1/bookings/BKG-2026000001/route-notification")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("ROUTE_NOTIFIED"))
                .andExpect(jsonPath("$.routeNotifiedBy").value("sales01"))
                .andExpect(jsonPath("$.routeNotifiedAt").exists());

        // 記録に残すのは「誰が」であり、システムではない
        verify(notifyShipper).notifyShipper("BKG-2026000001", "sales01");
    }

    @Test
    @DisplayName("経路設計者は荷主へ通知できない（403）")
    void routingPlannerCannotNotify() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/BKG-2026000001/route-notification")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isForbidden());

        verify(notifyShipper, never()).notifyShipper(any(), any());
    }

    /** できない状態への操作は 409（入力の誤りではない）。 */
    @Test
    @DisplayName("通知できない状態なら 409")
    void reportsConflictWhenNotifyingIsRefused() throws Exception {
        when(notifyShipper.notifyShipper(any(), any()))
                .thenThrow(new IllegalStateException("経路が決まった予約だけを荷主へ通知できます"));

        mockMvc.perform(post("/api/v1/bookings/BKG-2026000001/route-notification")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("営業が予約を確定できる")
    void confirms() throws Exception {
        when(confirmBooking.confirm(any())).thenReturn(Optional.of(BookingTestCargoes.notified().confirm()));

        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/confirm")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));
    }

    @Test
    @DisplayName("通知していない予約の確定は 409")
    void reportsConflictWhenConfirmingIsRefused() throws Exception {
        when(confirmBooking.confirm(any()))
                .thenThrow(new IllegalStateException("荷主へ通知した予約だけを確定できます"));

        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/confirm")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("営業が経路設計へ戻すと、経路の状態も作業待ちに戻る")
    void returnsToRouting() throws Exception {
        when(returnToRouting.returnToRouting(any()))
                .thenReturn(Optional.of(BookingTestCargoes.notified().returnToRouting()));

        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/return-to-routing")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("ROUTE_PROPOSED"))
                // BookingStatus だけ戻しても経路設計者の作業待ちに現れない
                .andExpect(jsonPath("$.routingStatus").value("ROUTING_REQUESTED"));
    }

    @Test
    @DisplayName("営業は到着期限と出発希望日を直せる")
    void revisesSchedule() throws Exception {
        when(reviseSchedule.revise(any(), any(), any())).thenReturn(Optional.of(BookingTestCargoes.booked()));

        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/schedule")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureDate\": \"2027-09-01\","
                                + " \"arrivalDeadline\": \"2027-10-10\"}"))
                .andExpect(status().isOk());

        verify(reviseSchedule).revise("BKG-2026000001",
                java.time.LocalDate.of(2027, java.time.Month.SEPTEMBER, 1),
                java.time.LocalDate.of(2027, java.time.Month.OCTOBER, 10));
    }

    /**
     * 形式の誤りは 400 で、利用者の言葉で返す（[ADR-016] 決定 2）。
     *
     * <p>読めない値をそのまま渡すと、集約が「必須です」と断り、利用者には
     * 「入力しているのに必須と言われる」と見える。
     */
    @Test
    @DisplayName("日付の形式が違えば 400（何が悪いかを伝える）")
    void rejectsMalformedDate() throws Exception {
        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/schedule")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivalDeadline\": \"2027/10/10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("到着期限")));

        verify(reviseSchedule, never()).revise(any(), any(), any());
    }

    @Test
    @DisplayName("経路設計者は予約を直せない（403）")
    void routingPlannerCannotRevise() throws Exception {
        mockMvc.perform(put("/api/v1/bookings/BKG-2026000001/schedule")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivalDeadline\": \"2027-10-10\"}"))
                .andExpect(status().isForbidden());

        verify(reviseSchedule, never()).revise(any(), any(), any());
    }

    /**
     * [ADR-021] 決定 7。<strong>広げる変更はしないが、狭まらないことを固定する</strong>。
     *
     * <p>追跡番号を発行するのは経路設計者である。確定した予約が見えなくなると、
     * US14 が 404 で成立しない。判定は 1 か所（`RoutingStatus#visibleToRoutingPlanner`）に
     * あるため、そこを触ると全部が変わる。
     */
    @Test
    @DisplayName("確定した予約も経路設計者が開ける（US14 の前提）")
    void confirmedBookingStaysVisibleToRoutingPlanner() throws Exception {
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.notified().confirm(), "丸紅商事")));

        mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));
    }

    /**
     * US13-3。<strong>件数だけ出しても仕事は進まない</strong>——そこから対象へ行けること。
     */
    @Test
    @DisplayName("経路設計者は予約の状態でも絞り込める（発行待ちを取り出す）")
    void filtersByBookingStatus() throws Exception {
        when(searchCargo.search(any(), any(), any(), any()))
                .thenReturn(new SearchCargoUseCase.Result(List.of(), 0L, 100));

        mockMvc.perform(get("/api/v1/bookings?bookingStatus=CONFIRMED")
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isOk());

        // 送った値が捨てられていないこと。捨てても一覧は返るため、結果を見るだけでは分からない
        verify(searchCargo).search(null, null, RoutingStatus.openToRoutingPlanner(),
                com.example.bookingms.domain.model.BookingStatus.CONFIRMED);
    }

    /**
     * US28-6。<strong>超える分は営業が読める場所に残す。</strong>
     *
     * <p>荷主へ伝えるのは営業である（通知は代替。[ADR-026] 決定 5）。超過の日数が
     * 経路を割り当てた直後の画面にしか出ないと、<strong>経路設計者がメモを取り損ねた
     * 時点で誰も伝えられなくなる</strong>。予約詳細を開けば読めることを固定する。
     */
    @Test
    @DisplayName("期限を超える経路の予約は、詳細でも何日超えるかを返す")
    void carriesDaysBeyondDeadlineOnDetail() throws Exception {
        // 期限は 2027-09-20。誤配のあと組み直した経路は 2027-09-25 着で 5 日超える。
        // **期限を超える旅程は再設計でしか作れない**（[ADR-026] 決定 4・5）——
        // 割り当てのときは期限で弾く
        Cargo beyond = BookingTestCargoes.routed()
                .misrouted("SGSIN", Instant.parse("2027-09-10T12:00:00Z"))
                .reassignItinerary(CargoItinerary.of(List.of(Leg.of(VoyageNumber.of("V0200"),
                        Location.of("SGSIN", "Singapore"), Location.of("USLAX", "Los Angeles"),
                        Instant.parse("2027-09-12T09:00:00Z"),
                        Instant.parse("2027-09-25T09:00:00Z")))));
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(beyond, "丸紅商事")));
        when(locations.timeZoneOf("USLAX"))
                .thenReturn(Optional.of(ZoneId.of("America/Los_Angeles")));

        mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysBeyondDeadline").value(5));
    }

    /**
     * IT10 レビュー低 15。<strong>誤配の港も名前で出す。</strong>
     *
     * <p>この画面は出発地・目的地・旅程の各区間を「名前（UN/LOCODE）」の形で出している。
     * 誤配のバナーだけが符号のままだと、担当者はそこで対訳表を引くことになる。
     *
     * <p><strong>名前はサーバが解決する。</strong>画面に対訳表を持たせない（他の項目と
     * 同じ形）。そして<strong>旅程からは引けない</strong>——誤配した港は定義上
     * 予定ルートの外にあり、旅程の中を探しても見つからない。地点マスタから引く。
     */
    @Test
    @DisplayName("誤配の港と現在地を、名前つきで返す")
    void carriesMisrouteLocationNames() throws Exception {
        Cargo misrouted = BookingTestCargoes.routed()
                .misrouted("SGSIN", Instant.parse("2027-09-10T12:00:00Z"));
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(misrouted, "丸紅商事")));
        when(locations.timeZoneOf("USLAX"))
                .thenReturn(Optional.of(ZoneId.of("America/Los_Angeles")));
        when(locations.findByUnLocode("SGSIN"))
                .thenReturn(Optional.of(Location.of("SGSIN", "Singapore")));

        mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.misroute.locationUnLocode").value("SGSIN"))
                .andExpect(jsonPath("$.misroute.locationName").value("Singapore"))
                .andExpect(jsonPath("$.lastHandlingLocationUnLocode").value("SGSIN"))
                .andExpect(jsonPath("$.lastHandlingLocationName").value("Singapore"));
    }

    /**
     * <strong>地点マスタに無い港でも、記録そのものは返す。</strong>
     *
     * <p>誤配は「予定していない港に降ろされた」事実であり、その港がマスタに載っている
     * 保証はない。名前が引けないことを理由に記録ごと落とすと、<strong>最も異常な
     * 誤配ほど画面から消える</strong>。
     */
    @Test
    @DisplayName("地点マスタに無い港の誤配でも、符号は返る")
    void keepsMisrouteWhenLocationNameIsUnknown() throws Exception {
        Cargo misrouted = BookingTestCargoes.routed()
                .misrouted("XXUNK", Instant.parse("2027-09-10T12:00:00Z"));
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(misrouted, "丸紅商事")));
        when(locations.timeZoneOf("USLAX"))
                .thenReturn(Optional.of(ZoneId.of("America/Los_Angeles")));
        when(locations.findByUnLocode("XXUNK")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.misroute.locationUnLocode").value("XXUNK"))
                .andExpect(jsonPath("$.misroute.locationName").doesNotExist());
    }

    /**
     * <strong>期限内なら値を出さない。</strong>毎回何かが出ると、超えている予約が
     * 埋もれる。
     */
    @Test
    @DisplayName("期限内の予約では、超過の日数を返さない")
    void omitsDaysBeyondDeadlineWithinDeadline() throws Exception {
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.routed(), "丸紅商事")));
        when(locations.timeZoneOf("USLAX"))
                .thenReturn(Optional.of(ZoneId.of("America/Los_Angeles")));

        mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysBeyondDeadline").doesNotExist());
    }

    /**
     * <strong>マスタが欠けても詳細は開く。</strong>割り当てのときは断る（直すべきは
     * こちら側の不備）が、読むだけの詳細まで落とすと、<strong>マスタの不備で予約が
     * 1 件も開けなくなる</strong>——超過の表示はそこまでの価値を持たない。
     */
    @Test
    @DisplayName("目的地の暦が引けなくても、詳細は開ける")
    void stillOpensWhenTimeZoneIsMissing() throws Exception {
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.routed(), "丸紅商事")));
        when(locations.timeZoneOf("USLAX")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysBeyondDeadline").doesNotExist());
    }

    /**
     * IT10 レビュー（user-representative 高 1・高 2）。<strong>開けない画面へ誘導しない。</strong>
     *
     * <p>誤配に最初に気づくのは追跡管理者であり、キャンセルを承認するのも追跡管理者である。
     * どちらの一覧からも予約詳細へ渡す導線を置いたが、<strong>押すと 403 になっていた</strong>。
     * 承認の判断には荷主・貨物種別・旅程が要る（IT10 返済枠 0.4）。
     *
     * <p><strong>読むだけである。</strong>操作の可否は集約の述語が決め、画面がロールで
     * 出し分ける。ここで開けるのは中身を読むことだけで、経路の割り当ても確定も行えない。
     */
    @Test
    @DisplayName("追跡管理者と荷役作業員は、予約の詳細を読める")
    void opensDetailToTrackerAndHandler() throws Exception {
        when(cargoes.findByBookingId("BKG-2026000001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.routed(), "丸紅商事")));
        when(locations.timeZoneOf("USLAX"))
                .thenReturn(Optional.of(ZoneId.of("America/Los_Angeles")));

        for (String[] who : List.of(new String[] {"tracker01", "ROLE_TRACKER"},
                new String[] {"handler01", "ROLE_HANDLER"})) {
            mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                            .header(AuthenticatedUser.USER_ID_HEADER, who[0])
                            .header(AuthenticatedUser.ROLES_HEADER, who[1]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value("BKG-2026000001"));
        }
    }

    /**
     * <strong>読み取りは詳細だけである。</strong>一覧まで開くと、追跡管理者が
     * 営業の抱えている案件を横断して眺められる——例外や承認から辿る 1 件を読むこととは
     * 別の話であり、US28・US30 のどちらも求めていない。
     */
    @Test
    @DisplayName("追跡管理者に予約の一覧は開かない")
    void keepsListClosedToTracker() throws Exception {
        mockMvc.perform(get("/api/v1/bookings")
                        .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("見つからない予約は 404")
    void reportsMissingBooking() throws Exception {
        when(confirmBooking.confirm(any())).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/bookings/BKG-9999999999/confirm")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isNotFound());
    }
}
