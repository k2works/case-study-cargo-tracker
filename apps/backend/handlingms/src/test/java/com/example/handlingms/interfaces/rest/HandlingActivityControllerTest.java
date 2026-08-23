package com.example.handlingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.handlingms.application.internal.RegisterHandlingActivityCommand;
import com.example.handlingms.application.internal.RegisterHandlingActivityUseCase;
import com.example.handlingms.application.port.CargoLookupUnavailableException;
import com.example.handlingms.application.port.HandlingActivityRepository;
import com.example.handlingms.application.port.LocationRepository;
import com.example.handlingms.domain.model.HandlingActivity;
import com.example.handlingms.domain.model.HandlingType;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 荷役作業の記録 API（US15・US16）。
 *
 * <p><strong>判定は集約が持つ</strong>。ここで確かめるのは、入口が正しい相手に頼み、
 * 断られたときに正しい形で返すことである。
 */
@WebMvcTest(HandlingActivityController.class)
@DisplayName("荷役作業 API")
class HandlingActivityControllerTest {

    private static final String BODY = """
            {"trackingNumber": "TRK-20260823-0001", "type": "RECEIVE",
             "locationUnLocode": "JPTYO", "completionTime": "2026-08-23T02:00:00Z"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterHandlingActivityUseCase registerActivity;

    @MockitoBean
    private HandlingActivityRepository activities;

    @MockitoBean
    private LocationRepository locations;

    @MockitoBean
    private com.example.handlingms.application.port.CargoSnapshotFinder cargoes;

    private static HandlingActivity received() {
        return HandlingActivity.restore(1L,
                com.example.handlingms.domain.model.CargoBookingId.of("BKG-2026000001"),
                HandlingType.RECEIVE, Location.of("JPTYO", "Tokyo"),
                Instant.parse("2026-08-23T02:00:00Z"), "handler01", null, null, false);
    }

    @Nested
    @DisplayName("荷役作業員として")
    class AsHandler {

        @Test
        @DisplayName("受領を記録できる")
        void registersReceive() throws Exception {
            when(registerActivity.register(any())).thenReturn(Optional.of(received()));

            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("RECEIVE"))
                    .andExpect(jsonPath("$.bookingId").value("BKG-2026000001"))
                    // 港は名前まで返す。画面が 5 文字のコードから引き直さずに済む
                    .andExpect(jsonPath("$.locationName").value("Tokyo"))
                    .andExpect(jsonPath("$.offRoute").value(false));
        }

        /**
         * <strong>すでに入っている記録は 409 で返す</strong>（IT8 返済枠 0.8）。
         *
         * <p>400 にすると、入力そのものは正しいのに作業員は打ち直しを試み、そのたびに
         * 同じ答えが返る。「もう入っている」ことが伝われば、次にすることは履歴を見ること
         * である。
         */
        @Test
        @DisplayName("同じ作業がすでに記録されていれば 409")
        void rejectsDuplicateRecording() throws Exception {
            when(registerActivity.register(any())).thenThrow(
                    new IllegalStateException("同じ作業がすでに記録されています。履歴を確認してください"));

            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("すでに記録されています")));
        }

        /**
         * <strong>作業者は名乗りから取る。</strong>
         *
         * <p>本文で受け取ると、他人の名前で記録できる。誰が記録したか分からない記録は
         * 監査に使えない。
         */
        @Test
        @DisplayName("作業者は名乗りから取る。本文の値は使わない")
        void takesTheOperatorFromThePrincipal() throws Exception {
            when(registerActivity.register(any())).thenReturn(Optional.of(received()));

            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"trackingNumber": "TRK-20260823-0001", "type": "RECEIVE",
                                     "locationUnLocode": "JPTYO",
                                     "completionTime": "2026-08-23T02:00:00Z",
                                     "operatorName": "別人"}
                                    """))
                    .andExpect(status().isCreated());

            org.mockito.ArgumentCaptor<RegisterHandlingActivityCommand> captor =
                    org.mockito.ArgumentCaptor.forClass(RegisterHandlingActivityCommand.class);
            verify(registerActivity).register(captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().operatorName())
                    .isEqualTo("handler01");
        }

        /**
         * <strong>予定外だったことが応答に載る</strong>（[ADR-023] 決定 3）。
         *
         * <p>予定どおりの記録だけを見ていると、応答で {@code offRoute} を潰しても緑のままに
         * なる。画面はこの値で警告を出すため、潰れると<strong>警告が一切出なくなる</strong>。
         */
        @Test
        @DisplayName("予定外の作業は、予定外として応答に載る")
        void reportsOffRoute() throws Exception {
            when(registerActivity.register(any())).thenReturn(Optional.of(HandlingActivity.restore(
                    2L, com.example.handlingms.domain.model.CargoBookingId.of("BKG-2026000001"),
                    HandlingType.UNLOAD, Location.of("SGSIN", "Singapore"),
                    Instant.parse("2026-08-23T02:00:00Z"), "handler01",
                    com.example.handlingms.domain.model.HandlingVoyageNumber.of("V0100"), null,
                    true)));

            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.offRoute").value(true))
                    .andExpect(jsonPath("$.voyageNumber").value("V0100"));
        }

        /** US15-6。番号を読み違えるのが最も多い。何を直せばよいかを伝える。 */
        @Test
        @DisplayName("存在しない追跡番号は 404 で理由を返す")
        void reportsUnknownTrackingNumber() throws Exception {
            when(registerActivity.register(any())).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isNotFound());
        }

        /**
         * <strong>「確かめられなかった」を 404 にしない。</strong>
         *
         * <p>「その番号は存在しません」と伝えると、作業員は番号を疑って打ち直し続ける。
         */
        @Test
        @DisplayName("貨物を確かめられないときは 503")
        void reportsLookupFailureAsUnavailable() throws Exception {
            when(registerActivity.register(any()))
                    .thenThrow(new CargoLookupUnavailableException("貨物を確認できませんでした",
                            new RuntimeException()));

            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isServiceUnavailable());
        }

        /**
         * 成功基準 3（API 層）。荷受人の確認がない引取を断る。
         *
         * <p>集約が断るので、入口は理由を伝えるだけである。
         */
        @Test
        @DisplayName("荷受人の確認がない引取は 400 で理由を返す")
        void rejectsClaimWithoutConfirmation() throws Exception {
            when(registerActivity.register(any()))
                    .thenThrow(new IllegalArgumentException("荷受人の確認は必須です"));

            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"trackingNumber": "TRK-20260823-0001", "type": "CLAIM",
                                     "locationUnLocode": "USLAX",
                                     "completionTime": "2026-08-23T02:00:00Z"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("荷受人の確認は必須です"));
        }

        @Test
        @DisplayName("日時の形式が違えば 400（何が悪いかを伝える）")
        void reportsMalformedTime() throws Exception {
            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"trackingNumber": "TRK-20260823-0001", "type": "RECEIVE",
                                     "locationUnLocode": "JPTYO", "completionTime": "きのう"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("ISO 8601")));

            verify(registerActivity, never()).register(any());
        }

        @Test
        @DisplayName("知らない種別は 400")
        void reportsUnknownType() throws Exception {
            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"trackingNumber": "TRK-20260823-0001", "type": "INSPECT",
                                     "locationUnLocode": "JPTYO",
                                     "completionTime": "2026-08-23T02:00:00Z"}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("権限")
    class Authorization {

        /**
         * <strong>認可を入力の検査より先に置く</strong>（[ADR-016]）。
         *
         * <p>順序を逆にすると、権限の無い呼び出しでも本文が不正なら 400 が返り、
         * 権限が無いはずの相手にエンドポイントの入力仕様を教えることになる。
         */
        @Test
        @DisplayName("本文が不正でも、権限が無ければ 403")
        void authorizesBeforeValidating() throws Exception {
            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());

            verify(registerActivity, never()).register(any());
        }

        /** 追跡管理者は結果を見る役割である。記録できると「見ている人が動かす」ことになる。 */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_SALES", "ROLE_ROUTING", "ROLE_TRACKER", "ROLE_SHIPPER",
            "ROLE_ACCOUNTANT", "ROLE_ADMIN"})
        @DisplayName("荷役作業員以外は記録できない")
        void onlyHandlersCanRegister(String role) throws Exception {
            mockMvc.perform(post("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());

            verify(registerActivity, never()).register(any());
        }

        /** 参照は追跡管理者にも開く。何が起きたかを追う役割である。 */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_HANDLER", "ROLE_TRACKER"})
        @DisplayName("荷役作業員と追跡管理者は履歴を見られる")
        void handlersAndTrackersCanRead(String role) throws Exception {
            when(activities.findByBookingId(any(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(List.of(received()));

            mockMvc.perform(get("/api/v1/handling").param("bookingId", "BKG-2026000001")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].type").value("RECEIVE"));
        }

        /**
         * <strong>追跡管理者が実際に使えること</strong>を確かめる。
         *
         * <p>メニューに出しておきながら開いても何もできない画面は、
         * 「壊れている」と受け取られる。追跡管理者が手元に持つのは追跡番号である。
         */
        @Test
        @DisplayName("追跡管理者は、追跡番号だけで履歴を引ける")
        void trackersCanReadHistoryByTrackingNumber() throws Exception {
            when(cargoes.findByTrackingNumber(any())).thenReturn(Optional.of(
                    com.example.handlingms.domain.model.CargoSnapshot.of("BKG-2026000001",
                            "JPTYO", "USLAX", List.of())));
            when(activities.findByBookingId(any(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(List.of(received()));

            mockMvc.perform(get("/api/v1/handling").param("trackingNumber", "TRK-20260823-0001")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].type").value("RECEIVE"));
        }

        @Test
        @DisplayName("知らない追跡番号の履歴は 404")
        void reportsNotFoundForUnknownTrackingNumber() throws Exception {
            when(cargoes.findByTrackingNumber(any())).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/handling").param("trackingNumber", "TRK-99999999-9999")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("追跡番号も予約番号も無ければ 400")
        void requiresOneOfTheIdentifiers() throws Exception {
            mockMvc.perform(get("/api/v1/handling")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("名乗らない要求は 400（ADR-007 のフィルタと同じ扱い）")
        void rejectsRequestWithoutPrincipal() throws Exception {
            mockMvc.perform(get("/api/v1/handling").param("bookingId", "BKG-2026000001"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("選択肢")
    class Choices {

        /** US15-3。自由入力にすると、綴りの揺れた港が記録に入り、照合が働かなくなる。 */
        @Test
        @DisplayName("作業場所は地点マスタから選ぶ")
        void offersLocationsFromTheMaster() throws Exception {
            when(locations.findAll()).thenReturn(List.of(Location.of("JPTYO", "Tokyo")));

            mockMvc.perform(get("/api/v1/handling/locations")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].unLocode").value("JPTYO"))
                    .andExpect(jsonPath("$[0].name").value("Tokyo"));
        }

        /**
         * <strong>種別ごとの要件はサーバが答える</strong>（[ADR-023] 決定 1）。
         *
         * <p>画面が「積込なら航海番号が要る」と書くと、規則が種別と画面の 2 か所に分かれる。
         */
        @Test
        @DisplayName("種別ごとの要件を返す")
        void describesTypeRequirements() throws Exception {
            mockMvc.perform(get("/api/v1/handling/types")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(HandlingType.values().length))
                    .andExpect(jsonPath("$[?(@.type=='LOAD')].requiresVoyageNumber")
                            .value(org.hamcrest.Matchers.hasItem(true)))
                    .andExpect(jsonPath("$[?(@.type=='CLAIM')].requiresConsigneeConfirmation")
                            .value(org.hamcrest.Matchers.hasItem(true)))
                    .andExpect(jsonPath("$[?(@.type=='RECEIVE')].label")
                            .value(org.hamcrest.Matchers.hasItem("受領")));
        }
    }
}
