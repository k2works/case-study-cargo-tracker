package com.example.bookingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookingms.application.internal.CancellationOutcome;
import com.example.bookingms.application.internal.DecideCancellationUseCase;
import com.example.bookingms.application.internal.RequestCancellationUseCase;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.CancellationRequest;
import com.example.shared.auth.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * キャンセル API（US30）。
 *
 * <p><strong>申請は営業担当者、承認は追跡管理者。</strong>自分の申請を自分で承認できると
 * 承認の意味が無くなる。
 *
 * <p><strong>認可は入力検証より先に置く</strong>（[ADR-016]）。
 */
@WebMvcTest(CancellationController.class)
@org.springframework.context.annotation.Import(CancellationControllerTest.FixedClock.class)
@DisplayName("キャンセル API")
class CancellationControllerTest {

    private static final String BOOKING_ID = "BKG-2026000001";
    private static final String REQUEST_BODY = """
            {"reason": "荷主都合"}
            """;
    private static final String APPROVE_BODY = """
            {"dischargeLocationUnLocode": "CNSHA", "decisionReason": "荷主と合意"}
            """;
    private static final String REJECT_BODY = """
            {"decisionReason": "積み替え済みのため"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestCancellationUseCase request;

    @MockitoBean
    private DecideCancellationUseCase decide;

    @MockitoBean
    private CargoRepository cargoes;

    /**
     * 日時の表示に使う。**業務タイムゾーンを固定する**——実時計を使うと、
     * 走らせた端末の設定で応答の日時が変わる。
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @org.springframework.context.annotation.Bean
        java.time.Clock clock() {
            return java.time.Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"),
                    java.time.ZoneId.of("Asia/Tokyo"));
        }
    }

    private static CancellationRequest awaiting() {
        return CancellationRequest.request(1L, "荷主都合", "sales01",
                Instant.parse("2026-09-05T00:00:00Z"), BookingStatus.IN_TRANSIT, true);
    }

    @Nested
    @DisplayName("誰が何をできるか（US30-4）")
    class WhoCanDoWhat {

        @Test
        @DisplayName("申請できるのは営業担当者だけ")
        void onlySalesCanRequest() throws Exception {
            when(request.request(anyString(), anyString(), anyString()))
                    .thenReturn(new CancellationOutcome(awaiting(), true));

            mockMvc.perform(post("/api/v1/bookings/" + BOOKING_ID + "/cancellation")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/bookings/" + BOOKING_ID + "/cancellation")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                    .andExpect(status().isForbidden());
        }

        /**
         * <strong>営業は自分の申請を承認できない。</strong>
         *
         * <p>できてしまうと、輸送中の貨物が誰の判断も経ずに止まる——承認の意味が無くなる。
         */
        @Test
        @DisplayName("承認・却下できるのは追跡管理者だけ")
        void onlyTrackerCanDecide() throws Exception {
            when(decide.approve(anyString(), anyString(), anyString(), any()))
                    .thenReturn(awaiting().approve("CNSHA", "tracker01", "合意",
                            Instant.parse("2026-09-06T00:00:00Z")));
            when(decide.reject(anyString(), anyString(), anyString()))
                    .thenReturn(awaiting().reject("tracker01", "積み替え済み",
                            Instant.parse("2026-09-06T00:00:00Z")));

            mockMvc.perform(put("/api/v1/bookings/" + BOOKING_ID + "/cancellation/approve")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/v1/bookings/" + BOOKING_ID + "/cancellation/approve")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/v1/bookings/" + BOOKING_ID + "/cancellation/reject")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON).content(REJECT_BODY))
                    .andExpect(status().isForbidden());
        }

        /** 承認待ちの一覧は追跡管理者のもの。営業には出さない。 */
        @Test
        @DisplayName("承認待ちの一覧を読めるのは追跡管理者だけ")
        void onlyTrackerCanListAwaiting() throws Exception {
            when(decide.awaitingDecision()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/cancellations")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/cancellations")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isForbidden());
        }

        /** 申請の行方は、申請した営業も承認する追跡管理者も読む。 */
        @Test
        @DisplayName("申請の行方は、営業も追跡管理者も読める")
        void bothCanReadTheRequest() throws Exception {
            when(decide.latestFor(anyString())).thenReturn(Optional.of(awaiting()));

            for (String[] user : new String[][] {
                {"sales01", "ROLE_SALES"}, {"tracker01", "ROLE_TRACKER"}}) {
                mockMvc.perform(get("/api/v1/bookings/" + BOOKING_ID + "/cancellation")
                                .header(AuthenticatedUser.USER_ID_HEADER, user[0])
                                .header(AuthenticatedUser.ROLES_HEADER, user[1]))
                        .andExpect(status().isOk());
            }
        }

        /**
         * <strong>[ADR-016]。認可は入力検証より先。</strong>
         *
         * <p>検証が先に走ると、権限の無い相手に「何が必須か」を教えることになる。
         */
        @Test
        @DisplayName("権限が無ければ、入力の中身を見る前に断る")
        void returns403BeforeValidatingTheBody() throws Exception {
            mockMvc.perform(post("/api/v1/bookings/" + BOOKING_ID + "/cancellation")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());

            verify(request, never()).request(anyString(), any(), anyString());
        }
    }

    @Nested
    @DisplayName("応答の形")
    class ResponseShape {

        /** **承認を待つかどうかをサーバが答える。**画面が状態名を見比べない。 */
        @Test
        @DisplayName("申請の結果に、承認を待つかどうかが含まれる")
        void tellsWhetherApprovalIsNeeded() throws Exception {
            when(request.request(anyString(), anyString(), anyString()))
                    .thenReturn(new CancellationOutcome(awaiting(), true));

            mockMvc.perform(post("/api/v1/bookings/" + BOOKING_ID + "/cancellation")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.awaitingApproval").value(true))
                    // **状態の読み方はサーバが返す**（画面が対訳表を持たない）
                    .andExpect(jsonPath("$.request.statusLabel").value("承認待ち"))
                    .andExpect(jsonPath("$.request.bookingStatusAtRequestLabel").value("輸送中"))
                    // **日時は業務の時刻で返す**（通関の応答と同じ形）。生の ISO を返すと
                    // 追跡管理者が読み替えることになり、同じ画面群で形式が食い違う
                    .andExpect(jsonPath("$.request.requestedAt").value("2026-09-05 09:00"));
        }

        /** 申請が無ければ本文なし。**空の申請を作って返さない**。 */
        @Test
        @DisplayName("申請が無ければ本文なしで返す")
        void returnsNoContentWithoutARequest() throws Exception {
            when(decide.latestFor(anyString())).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/bookings/" + BOOKING_ID + "/cancellation")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isNoContent());
        }

        /** 候補外の港は 400。**入力の誤り**であり、業務の状態の問題ではない。 */
        @Test
        @DisplayName("候補に無い港での承認は 400")
        void returns400ForAPortOutsideTheCandidates() throws Exception {
            when(decide.approve(anyString(), anyString(), anyString(), any()))
                    .thenThrow(new IllegalArgumentException(
                            "その港では荷降しできません。候補から選んでください"));

            mockMvc.perform(put("/api/v1/bookings/" + BOOKING_ID + "/cancellation/approve")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "その港では荷降しできません。候補から選んでください"));
        }

        /** 承認待ちが 2 件目なら 409。**入力ではなく状態の問題**である。 */
        @Test
        @DisplayName("承認待ちがあるときの申請は 409")
        void returns409WhenAlreadyAwaiting() throws Exception {
            when(request.request(anyString(), anyString(), anyString()))
                    .thenThrow(new IllegalStateException(
                            "この予約には承認待ちのキャンセル申請があります"));

            mockMvc.perform(post("/api/v1/bookings/" + BOOKING_ID + "/cancellation")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                    .andExpect(status().isConflict());
        }
    }
}
