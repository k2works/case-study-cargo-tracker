package com.example.bookingms.interfaces.rest;
import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookingms.application.internal.BookCargoUseCase;
import com.example.bookingms.application.internal.RequestRoutingUseCase;
import com.example.bookingms.application.internal.SearchCargoUseCase;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoStatus;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CargoBookingController.class)
@DisplayName("貨物予約 API")
class CargoBookingControllerTest {

    private static final String BODY = """
            {"shipperId": 1, "type": "GENERAL", "weightKg": 12000, "quantity": 20,
             "description": "電子部品", "lengthCm": 120, "widthCm": 80, "heightCm": 100,
             "originUnLocode": "JPTYO", "destinationUnLocode": "USLAX",
             "departureDate": "2027-09-01", "arrivalDeadline": "2027-09-20"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookCargoUseCase bookCargo;

    @MockitoBean
    private SearchCargoUseCase searchCargo;

    @MockitoBean
    private RequestRoutingUseCase requestRouting;

    @MockitoBean
    private com.example.bookingms.application.internal.AssignRouteUseCase assignRoute;

    @MockitoBean
    private CargoRepository cargoes;

    @MockitoBean
    private LocationRepository locations;

    /** 経路設計者へ引き渡し済みの予約。 */
    private static Cargo requested() {
        return booked().requestRouting();
    }

    /** 経路が決まった予約（ADR-020 決定 2）。 */
    private static Cargo routed() {
        return requested().assignItinerary(
                com.example.bookingms.domain.model.CargoItinerary.of(java.util.List.of(
                        com.example.bookingms.domain.model.Leg.of(
                                com.example.bookingms.domain.model.VoyageNumber.of("V0100"),
                                Location.of("JPTYO", "Tokyo"),
                                Location.of("USLAX", "Los Angeles"),
                                java.time.Instant.parse("2027-09-02T09:00:00Z"),
                                java.time.Instant.parse("2027-09-15T09:00:00Z")))),
                java.time.ZoneId.of("America/Los_Angeles"));
    }

    private static Cargo booked() {
        return Cargo.restore(1L, BookingId.of("BKG-2026000001"), 1L, CargoStatus.preliminary(),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(Location.of("JPTYO", "Tokyo"),
                        Location.of("USLAX", "Los Angeles"), LocalDate.of(2027, Month.SEPTEMBER, 1),
                        LocalDate.of(2027, Month.SEPTEMBER, 20)));
    }

    @Nested
    @DisplayName("営業担当者として")
    class AsSales {

        @Test
        @DisplayName("予約を登録すると 201・予約番号・仮受付を返す")
        void books() throws Exception {
            when(bookCargo.book(any())).thenReturn(booked());

            mockMvc.perform(post("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.bookingId").value("BKG-2026000001"))
                    .andExpect(jsonPath("$.bookingStatus").value("PRELIMINARY"))
                    // 「まだ動いていない」は空欄ではなく意味のある状態（ADR-009）
                    .andExpect(jsonPath("$.transportStatus").value("NOT_RECEIVED"))
                    .andExpect(jsonPath("$.routingStatus").value("NOT_ROUTED"))
                    // 地点は名称まで返す。画面がコードから名称を引き直さずに済む
                    .andExpect(jsonPath("$.originName").value("Tokyo"))
                    .andExpect(jsonPath("$.destinationName").value("Los Angeles"));
        }

        @Test
        @DisplayName("入力の誤りは理由を添えて 400 で返す")
        void reportsInvalidInput() throws Exception {
            when(bookCargo.book(any()))
                    .thenThrow(new IllegalArgumentException("指定された荷主が見つかりません: 999"));

            mockMvc.perform(post("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY.replace("\"shipperId\": 1", "\"shipperId\": 999")))
                    .andExpect(status().isBadRequest())
                    // 入力した値は画面に返さない。マニュアルの「よくある入力の誤り」の表と
                    // 字面が合わなくなり、利用者が表で探せなくなる
                    .andExpect(jsonPath("$.message").value("指定された荷主が見つかりません"));
        }

        @Test
        @DisplayName("一覧は総件数と上限を添えて返す")
        void searches() throws Exception {
            when(searchCargo.search(null, null, null))
                    .thenReturn(new SearchCargoUseCase.Result(
                            List.of(new CargoSummary(booked(), "丸紅商事")), 1L, 100));

            mockMvc.perform(get("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookings[0].bookingId").value("BKG-2026000001"))
                    // 社名で探せる一覧なのに結果に社名が無いと、同名の別会社を見分けられない
                    .andExpect(jsonPath("$.bookings[0].shipperName").value("丸紅商事"))
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.limit").value(100))
                    // 上限で切ったことを黙っていると「全件見た」と受け取られる
                    .andExpect(jsonPath("$.truncated").value(false));
        }

        @Test
        @DisplayName("種別で絞り込める")
        void filtersByType() throws Exception {
            when(searchCargo.search(CargoType.HAZARDOUS, null, null))
                    .thenReturn(new SearchCargoUseCase.Result(List.of(), 0L, 100));

            mockMvc.perform(get("/api/v1/bookings")
                            .param("type", "HAZARDOUS")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isOk());

            verify(searchCargo).search(CargoType.HAZARDOUS, null, null);
        }

        @Test
        @DisplayName("地点の選択肢を返す")
        void listsLocations() throws Exception {
            when(locations.findAll()).thenReturn(List.of(Location.of("JPTYO", "Tokyo")));

            mockMvc.perform(get("/api/v1/bookings/locations")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].unLocode").value("JPTYO"))
                    .andExpect(jsonPath("$[0].name").value("Tokyo"));
        }
    }

    @Nested
    @DisplayName("経路設計への引き渡し（US06）")
    class RoutingHandover {

        @Test
        @DisplayName("営業担当者は経路設計を依頼できる")
        void salesRequestsRouting() throws Exception {
            when(requestRouting.request("BKG-2026000001"))
                    .thenReturn(Optional.of(new CargoSummary(booked().requestRouting(), "丸紅商事")));

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
                    .thenReturn(Optional.of(new CargoSummary(requested(), "丸紅商事")));

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
                    .thenReturn(Optional.of(new CargoSummary(booked(), "丸紅商事")));

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
                    .thenReturn(Optional.of(new CargoSummary(booked(), "丸紅商事")));
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
                    .thenReturn(Optional.of(new CargoSummary(routed(), "丸紅商事")));

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
                    .thenReturn(Optional.of(new CargoSummary(booked(), "丸紅商事")));

            mockMvc.perform(get("/api/v1/bookings/BKG-2026000001")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("経路が決まっていない予約は旅程を返さない（空の配列にしない）")
        void unroutedCargoHasNoItinerary() throws Exception {
            when(cargoes.findByBookingId("BKG-2026000001"))
                    .thenReturn(Optional.of(new CargoSummary(booked(), "丸紅商事")));

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
            when(searchCargo.search(any(), any(), any()))
                    .thenReturn(new SearchCargoUseCase.Result(List.of(), 0L, 100));

            mockMvc.perform(get("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isOk());

            verify(searchCargo).search(null, null, RoutingStatus.ROUTING_REQUESTED);
        }

        /** 絞り込みを外そうとしても、経路設計者には効かない。 */
        @Test
        @DisplayName("経路設計者が条件を外しても全件は見えない")
        void routingPlannerCannotWidenTheList() throws Exception {
            when(searchCargo.search(any(), any(), any()))
                    .thenReturn(new SearchCargoUseCase.Result(List.of(), 0L, 100));

            mockMvc.perform(get("/api/v1/bookings")
                            .param("routingStatus", "NOT_ROUTED")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isOk());

            verify(searchCargo).search(null, null, RoutingStatus.ROUTING_REQUESTED);
        }
    }

    @Nested
    @DisplayName("営業担当者以外として")
    class AsOthers {

        /**
         * 荷主ロールにも開かない（ADR-008）。
         *
         * <p>利用者と荷主を結ぶキーが無く「自分の予約だけ」に絞り込めないため、開くと
         * 全荷主の予約が見える。「まだ作っていない」を「開いていない」と取り違えないよう、
         * 荷主ロールが 403 を受けることを明示的に確かめる。
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "ROLE_SHIPPER", "ROLE_ROUTING", "ROLE_HANDLER", "ROLE_TRACKER",
            "ROLE_ACCOUNTANT", "ROLE_ADMIN"
        })
        @DisplayName("登録は 403 で拒否し、ユースケースを呼ばない")
        void rejectsBooking(String role) throws Exception {
            mockMvc.perform(post("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());

            verify(bookCargo, never()).book(any());
        }


        /**
         * 実環境で見つかった欠陥の回帰（IT3 レビュー）。
         *
         * <p>{@code @Valid} は引数の解決時に走るため、権限の無い呼び出しでも本文が不正なら
         * 400 が返っていた。本人には「この操作はできない」ではなく「入力を直せ」と伝わり、
         * 権限が無いはずの相手にエンドポイントの入力仕様を教えることになる。
         * 直した場所ではなく、欠陥が起きたこの場所に固定する。
         */
        @Test
        @DisplayName("本文が不正でも、権限が無ければ 403")
        void checksPermissionBeforeValidation() throws Exception {
            mockMvc.perform(post("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());

            verify(bookCargo, never()).book(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "ROLE_SHIPPER", "ROLE_HANDLER", "ROLE_TRACKER",
            "ROLE_ACCOUNTANT", "ROLE_ADMIN"
        })
        @DisplayName("参照も 403 で拒否する")
        void rejectsSearch(String role) throws Exception {
            mockMvc.perform(get("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role))
                    .andExpect(status().isForbidden());

            verify(searchCargo, never()).search(any(), any(), any());
        }

        /**
         * クレームが無い呼び出しは処理しない。
         *
         * <p>実際の経路では {@code AuthenticatedUserFilter}（ADR-007）が先に 401 で弾く。
         * ここはコントローラ単体の切り口であり、フィルタを通らないため 400 になる。
         * 401 になることは {@code AuthenticatedUserHeaderRequiredTest} が確かめる。
         */
        @Test
        @DisplayName("クレームが無ければ処理しない（Gateway を通っていない呼び出し）")
        void rejectsRequestWithoutClaims() throws Exception {
            mockMvc.perform(get("/api/v1/bookings")).andExpect(status().isBadRequest());
        }
    }

    /** 経路の割り当て（US09・[ADR-019]）。 */
    @Nested
    @DisplayName("経路の割り当て")
    class AssigningRoute {

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
            when(assignRoute.assign(any(), any(), any())).thenReturn(Optional.of(routed()));

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
                    .thenThrow(new IllegalStateException("選んだ経路はもう使えません"));

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
    }
}
