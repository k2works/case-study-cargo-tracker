package com.example.handlingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.handlingms.application.internal.ManageCustomsDeclarationUseCase;
import com.example.handlingms.application.internal.RegisterCustomsDeclarationCommand;
import com.example.handlingms.application.internal.RegisterCustomsDeclarationUseCase;
import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.CustomsDeclaration;
import com.example.handlingms.domain.model.CustomsStatus;
import com.example.handlingms.domain.model.DeclarationNumber;
import com.example.handlingms.domain.model.HandlingTrackingNumber;
import com.example.shared.auth.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
 * 通関申告 API（US29・[ADR-025] 決定 6）。
 *
 * <p><strong>登録は荷役作業員、状態の更新は追跡管理者。閲覧は両方。</strong>
 * 追跡管理者は状態を更新する側であり、申告そのものは出さない。
 *
 * <p><strong>認可は入力検証より先に置く</strong>（[ADR-016]）。検証が先に走ると、
 * 権限の無い相手に入力仕様を教えることになる。
 */
@WebMvcTest(CustomsController.class)
@DisplayName("通関申告 API")
class CustomsControllerTest {

    private static final String REGISTER_BODY = """
            {"trackingNumber": "TRK-20260823-0001", "declarationNumber": "DEC-0001",
             "declaredAt": "2027-09-02T00:00:00Z"}
            """;

    private static final String STATUS_BODY = """
            {"status": "CLEARED", "reason": "書類確認により通関完了"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterCustomsDeclarationUseCase register;

    @MockitoBean
    private ManageCustomsDeclarationUseCase manage;

    @MockitoBean
    private Clock clock;

    /**
     * 業務の暦は<strong>サーバが決める</strong>。応答の日時整形と留置の判定に要る。
     *
     * <p>差し替えを忘れると {@code zone} が null で落ちる——**時計をモックにすると
     * 「今日」がどこから来るかが見えなくなる**ので、ここで明示しておく。
     */
    @org.junit.jupiter.api.BeforeEach
    void stubTheBusinessCalendar() {
        when(manage.today()).thenReturn(java.time.LocalDate.of(2027, 9, 3));
        when(manage.zone()).thenReturn(ZoneId.of("Asia/Tokyo"));
    }

    private static CustomsDeclaration declaration() {
        return CustomsDeclaration.declare(DeclarationNumber.of("DEC-0001"),
                CargoBookingId.of("BKG-2026000001"),
                HandlingTrackingNumber.of("TRK-20260823-0001"),
                Instant.parse("2027-09-02T00:00:00Z"));
    }

    @Nested
    @DisplayName("誰が何をできるか（[ADR-025] 決定 6）")
    class WhoCanDoWhat {

        /** 申告を出すのは荷役作業員である。 */
        @Test
        @DisplayName("登録できるのは荷役作業員だけ")
        void onlyHandlerCanRegister() throws Exception {
            when(register.register(any())).thenReturn(declaration());

            mockMvc.perform(post("/api/v1/customs")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/customs")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                    .andExpect(status().isForbidden());
        }

        /** 状態を更新するのは追跡管理者である。 */
        @Test
        @DisplayName("状態を更新できるのは追跡管理者だけ")
        void onlyTrackerCanUpdateStatus() throws Exception {
            when(manage.updateStatus(anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(declaration()));

            mockMvc.perform(put("/api/v1/customs/1/status")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/v1/customs/1/status")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY))
                    .andExpect(status().isForbidden());
        }

        /** 荷役作業員は自分が出した申告の行方を追う。追跡管理者は督促のために読む。 */
        @Test
        @DisplayName("読むのは両方できる")
        void bothRolesCanRead() throws Exception {
            when(manage.search(any(), any(), any())).thenReturn(List.of(declaration()));

            for (String[] user : new String[][] {
                {"handler01", "ROLE_HANDLER"}, {"tracker01", "ROLE_TRACKER"}}) {
                mockMvc.perform(get("/api/v1/customs")
                                .header(AuthenticatedUser.USER_ID_HEADER, user[0])
                                .header(AuthenticatedUser.ROLES_HEADER, user[1]))
                        .andExpect(status().isOk());
            }
        }

        /** 営業には開かない。通関は港での業務である。 */
        @Test
        @DisplayName("営業は読むこともできない")
        void salesCannotRead() throws Exception {
            mockMvc.perform(get("/api/v1/customs")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isForbidden());
        }

        /**
         * <strong>[ADR-016]。認可は入力検証より先。</strong>
         *
         * <p>検証が先に走ると、権限の無い相手に「何が必須か」を教えることになる。
         * 中身が空でも 400 ではなく 403 で返る。
         */
        @Test
        @DisplayName("権限が無ければ、入力の中身を見る前に断る")
        void returns403BeforeValidatingTheBody() throws Exception {
            mockMvc.perform(post("/api/v1/customs")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());

            verify(register, never()).register(any(RegisterCustomsDeclarationCommand.class));
        }
    }

    @Nested
    @DisplayName("応答の形")
    class ResponseShape {

        @Test
        @DisplayName("一覧は、状態の読み方と留置の経過を添えて返す")
        void returnsLabelsAndHeldDays() throws Exception {
            when(manage.search(any(), any(), any())).thenReturn(List.of(declaration()));

            mockMvc.perform(get("/api/v1/customs")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                    .andExpect(status().isOk())
                    // **画面が対訳表を持たない**（[ADR-023] 決定 1 と同じ形）
                    .andExpect(jsonPath("$[0].statusLabel").value("審査中"))
                    .andExpect(jsonPath("$[0].heldOverdue").value(false));
        }

        /** 選択肢はサーバが返す。**画面が一覧を持たない**。 */
        @Test
        @DisplayName("通関状態の選択肢を返す")
        void returnsTheStatusChoices() throws Exception {
            mockMvc.perform(get("/api/v1/customs/statuses")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(CustomsStatus.values().length));
        }

        /** 決定 7 に触れる登録は 409 で返す。400 だと「入力が悪い」と読まれる。 */
        @Test
        @DisplayName("決着していない申告があるときの登録は 409")
        void returns409WhenAnotherDeclarationIsUnsettled() throws Exception {
            when(register.register(any()))
                    .thenThrow(new IllegalStateException("この貨物には決着していない通関申告があります"));

            mockMvc.perform(post("/api/v1/customs")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "この貨物には決着していない通関申告があります"));
        }

        @Test
        @DisplayName("知らない申告 ID は 404")
        void returns404ForUnknownDeclaration() throws Exception {
            when(manage.find(anyLong())).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/customs/999")
                            .header(AuthenticatedUser.USER_ID_HEADER, "tracker01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_TRACKER"))
                    .andExpect(status().isNotFound());
        }
    }
}
