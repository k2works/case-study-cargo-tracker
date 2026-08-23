package com.example.trackingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import com.example.trackingms.application.internal.ManageTrackingUseCase;
import com.example.trackingms.domain.model.ExceptionType;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingBookingId;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TrackingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
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
 * 貨物状態の管理（US17・US19・US20）。
 *
 * <p><strong>判定は集約が持つ。</strong>ここで確かめるのは、入口が正しい相手に頼み、
 * 断られたときに正しい形で返すことである。
 */
@WebMvcTest(TrackingManagementController.class)
@org.springframework.context.annotation.Import(TrackingManagementControllerTest.ClockConfig.class)
@DisplayName("貨物状態の管理 API")
class TrackingManagementControllerTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final String NUMBER = "TRK-20260823-0001";
    private static final Instant NOW = Instant.parse("2027-09-03T00:00:00Z");

    private static final String UPDATE_BODY = """
            {"trackingNumber": "TRK-20260823-0001", "status": "ONBOARD_CARRIER",
             "locationUnLocode": "JPTYO", "occurredAt": "2027-09-03T00:00:00Z"}
            """;

    @org.springframework.boot.test.context.TestConfiguration
    static class ClockConfig {
        /**
         * 表示の暦は業務のタイムゾーン（[ADR-010]）。
         *
         * <p><strong>止まった時計を使う。</strong>検査で「いま」を読むと、実行した時刻に
         * よって結果が変わる。ここで要るのは暦であって現在時刻ではない。
         */
        @org.springframework.context.annotation.Bean
        java.time.Clock businessClock() {
            return java.time.Clock.fixed(java.time.Instant.parse("2026-08-23T00:00:00Z"),
                    java.time.ZoneId.of("Asia/Tokyo"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageTrackingUseCase manage;

    private static TrackingActivity loaded() {
        return TrackingActivity.start(TrackingNumber.of(NUMBER),
                        TrackingBookingId.of("BKG-2026000004"), TOKYO, LOS_ANGELES,
                        LocalDate.of(2027, Month.OCTOBER, 20))
                .afterHandling("RECEIVE", "JPTYO")
                .afterHandling("LOAD", "JPTYO");
    }

    @Nested
    @DisplayName("追跡管理者として")
    class AsTracker {

        @Test
        @DisplayName("状態を手で更新できる")
        void updatesStatus() throws Exception {
            when(manage.updateStatus(any(), any(), any(), any()))
                    .thenReturn(Optional.of(loaded()
                            .updateManually(TrackingStatus.ONBOARD_CARRIER, TOKYO, NOW)));
            when(manage.events(any())).thenReturn(List.of());

            mockMvc.perform(post("/api/v1/tracking/manage")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ONBOARD_CARRIER"))
                    .andExpect(jsonPath("$.statusLabel").value("輸送中"));
        }

        /**
         * <strong>戻る向きの更新は断る</strong>（[ADR-024] 決定 1）。
         *
         * <p>集約が投げる {@code IllegalArgumentException} を 400 で返す。
         * <strong>直す手段を伝える</strong>——「できません」で終わらせない。
         */
        @Test
        @DisplayName("戻る向きの更新は、直す手段を添えて断る")
        void rejectsBackwardUpdate() throws Exception {
            when(manage.updateStatus(any(), any(), any(), any())).thenThrow(
                    new IllegalArgumentException(
                            "前の状態には戻せません。誤りを直すには、例外として起票してください"));

            mockMvc.perform(post("/api/v1/tracking/manage")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("例外として起票")));
        }

        /**
         * <strong>進める先だけを返す。</strong>
         *
         * <p>戻る向きの選択肢を出しておいて断るのは、押せるのに断られる操作を出すことである。
         */
        @Test
        @DisplayName("手で進められる状態だけを返す")
        void returnsOnlyAdvanceableStatuses() throws Exception {
            when(manage.find(NUMBER)).thenReturn(Optional.of(loaded()));

            mockMvc.perform(get("/api/v1/tracking/manage/" + NUMBER + "/statuses")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("ONBOARD_CARRIER"))
                    // 積込済みより前へは戻せない
                    .andExpect(jsonPath("$[?(@.status == 'RECEIVED')]").isEmpty())
                    .andExpect(jsonPath("$[?(@.status == 'EXCEPTION')]").isEmpty());
        }

        /** [ADR-024] 決定 2。多重起票は 409 で断る。 */
        @Test
        @DisplayName("未解決の例外があるあいだの起票は 409")
        void rejectsASecondException() throws Exception {
            when(manage.raiseException(any(), any(), any())).thenThrow(
                    new IllegalStateException("この貨物には未解決の例外があります。例外を起票できません"));

            mockMvc.perform(post("/api/v1/tracking/manage/exceptions")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"trackingNumber": "TRK-20260823-0001",
                                     "exceptionType": "DAMAGE", "description": "破損"}
                                    """))
                    .andExpect(status().isConflict());
        }

        /**
         * <strong>自動で検知する種別は、選択肢に出さないだけでなく断る</strong>
         * （[ADR-024] 決定 11）。
         *
         * <p>API を直接叩けば送れるため、断る側にも規則を置く。
         */
        @Test
        @DisplayName("誤配・税関保留を送っても断る")
        void rejectsAutoDetectedTypes() throws Exception {
            when(manage.raiseException(any(), any(), any())).thenThrow(
                    new IllegalArgumentException("誤配 は自動で検知されるため、手では起票できません"));

            mockMvc.perform(post("/api/v1/tracking/manage/exceptions")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"trackingNumber": "TRK-20260823-0001",
                                     "exceptionType": "MISROUTE", "description": "誤配"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        /** [ADR-024] 決定 11。**起票できる 3 種別だけを返す**。 */
        @Test
        @DisplayName("起票できる種別だけを返す")
        void listsOnlyRaisableTypes() throws Exception {
            mockMvc.perform(get("/api/v1/tracking/manage/exception-types")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[?(@.exceptionType == 'MISROUTE')]").isEmpty())
                    .andExpect(jsonPath("$[?(@.exceptionType == 'CUSTOMS_HOLD')]").isEmpty())
                    // 紛失だけが緊急（決定 3）。画面はこの値でしか判断しない
                    .andExpect(jsonPath("$[?(@.exceptionType == 'LOST')].urgent")
                            .value(org.hamcrest.Matchers.contains(true)));
        }

        /** 横断規約。**件数を出すだけにしない**——一覧へ辿れる。 */
        @Test
        @DisplayName("未解決の例外の件数と、緊急の件数を返す")
        void countsOpenExceptions() throws Exception {
            TrackingActivity urgent = loaded()
                    .raiseException(ExceptionType.LOST, "所在不明", NOW);
            TrackingActivity delayed = loaded()
                    .raiseException(ExceptionType.DELAY, "遅延", NOW);
            when(manage.withOpenExceptions()).thenReturn(List.of(urgent, delayed));

            mockMvc.perform(get("/api/v1/tracking/manage/exceptions/open")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(2))
                    .andExpect(jsonPath("$.urgentCount").value(1));
        }
    }

    @Nested
    @DisplayName("荷役作業員として")
    class AsHandler {

        /**
         * <strong>起票は荷役作業員にも開く</strong>（US20 のアクターは 2 つ）。
         *
         * <p>破損・紛失に最初に気づくのは港にいる人である。追跡管理者だけに絞ると、
         * 気づいた人が伝える手段を持たない。
         */
        @Test
        @DisplayName("例外を起票できる")
        void canRaiseException() throws Exception {
            when(manage.raiseException(any(), any(), any())).thenReturn(
                    Optional.of(loaded().raiseException(ExceptionType.DAMAGE, "破損", NOW)));
            when(manage.events(any())).thenReturn(List.of());

            mockMvc.perform(post("/api/v1/tracking/manage/exceptions")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"trackingNumber": "TRK-20260823-0001",
                                     "exceptionType": "DAMAGE", "description": "破損"}
                                    """))
                    .andExpect(status().isOk());
        }

        /**
         * <strong>状態は動かせない。</strong>
         *
         * <p>荷役作業員は記録した作業から追跡が動く経路をすでに持っており、そのうえで
         * 手でも動かせると、同じ貨物が 2 つの経路から動く。
         */
        @Test
        @DisplayName("状態は手で動かせない")
        void cannotUpdateStatus() throws Exception {
            mockMvc.perform(post("/api/v1/tracking/manage")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                    .andExpect(status().isForbidden());
        }

        /** 解決も追跡管理者だけ。気づいた人が閉じられると「見つからないまま解決」になりうる。 */
        @Test
        @DisplayName("例外は解決できない")
        void cannotResolveException() throws Exception {
            mockMvc.perform(post("/api/v1/tracking/manage/exceptions/1/resolve")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"trackingNumber": "TRK-20260823-0001", "exceptionId": 1,
                                     "resolutionNotes": "直しました", "newEstimatedArrival": null}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("担当外のロールとして")
    class AsOthers {

        @Test
        @DisplayName("何もできない")
        void cannotDoAnything() throws Exception {
            mockMvc.perform(get("/api/v1/tracking/manage/" + NUMBER)
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isForbidden());
        }

        /** [ADR-016]。**認可は入力検証より先**——権限の無い相手に入力仕様を教えない。 */
        @Test
        @DisplayName("壊れた本文でも、認可が先に断る")
        void authorizesBeforeValidating() throws Exception {
            mockMvc.perform(post("/api/v1/tracking/manage")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"trackingNumber": "", "status": "NOPE",
                                     "locationUnLocode": "", "occurredAt": "きのう"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }
}
