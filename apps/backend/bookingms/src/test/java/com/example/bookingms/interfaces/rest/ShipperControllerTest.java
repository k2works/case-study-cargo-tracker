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

import com.example.bookingms.domain.model.commands.RegisterShipperCommand;
import com.example.bookingms.domain.model.valueobjects.ContractNumber;
import com.example.bookingms.domain.model.valueobjects.CorporateContract;
import com.example.bookingms.domain.model.valueobjects.DiscountRate;
import java.math.BigDecimal;
import org.mockito.ArgumentCaptor;
import com.example.bookingms.application.internal.commandservices.EditShipperUseCase;
import com.example.bookingms.application.internal.commandservices.RegisterShipperUseCase;
import com.example.bookingms.application.internal.queryservices.SearchShipperUseCase;
import com.example.bookingms.application.internal.commandservices.RegistrationOutcome;
import com.example.bookingms.domain.model.aggregates.Shipper;
import com.example.bookingms.domain.model.valueobjects.ShipperProfile;
import com.example.bookingms.domain.model.valueobjects.ShipperType;
import com.example.shared.auth.AuthenticatedUser;
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

@WebMvcTest(ShipperController.class)
@DisplayName("荷主 API")
class ShipperControllerTest {

    private static final String BODY = """
            {"type": "INDIVIDUAL", "name": "山田太郎", "email": "yamada@example.com",
             "address": "東京都千代田区 1-1-1", "phone": "03-1234-5678", "registerAnyway": false}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterShipperUseCase useCase;

    @MockitoBean
    private SearchShipperUseCase searchUseCase;

    @MockitoBean
    private EditShipperUseCase editUseCase;

    /** 法人の荷主。種別の変更を断ることを確かめるために使う。 */
    private static Shipper corporate() {
        return Shipper.restore(1L, "SHP-000002", ShipperType.CORPORATE,
                com.example.bookingms.domain.model.valueobjects.ShipperProfile.restore(
                        "丸紅商事", "corp@example.com", "東京都千代田区 1-1-1", null),
                new com.example.bookingms.domain.model.valueobjects.CorporateContract(
                        ContractNumber.of("CN-0001"), null));
    }

    private static Shipper existing() {
        return Shipper.restore(
                1L, "SHP-000001", ShipperType.INDIVIDUAL, "山田太郎", "yamada@example.com",
                "東京都千代田区 1-1-1", "03-1234-5678");
    }

    @Nested
    @DisplayName("営業担当者として")
    class AsSales {

        @Test
        @DisplayName("荷主を登録すると 201 と荷主 ID を返す")
        void registers() throws Exception {
            when(useCase.register(any()))
                    .thenReturn(new RegistrationOutcome.Registered(existing()));

            mockMvc.perform(post("/api/v1/shippers")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.shipperCode").value("SHP-000001"));
        }

        @Test
        @DisplayName("同じメールアドレスがあれば 409 と既存の荷主を返す")
        void reportsDuplicate() throws Exception {
            when(useCase.register(any()))
                    .thenReturn(new RegistrationOutcome.DuplicateFound(existing()));

            // エラーではなく問いかけ。画面はこの情報でどちらを使うか選ばせる
            mockMvc.perform(post("/api/v1/shippers")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.existing.shipperCode").value("SHP-000001"))
                    .andExpect(jsonPath("$.existing.name").value("山田太郎"));
        }

        @Test
        @DisplayName("荷主を検索できる")
        void searches() throws Exception {
            when(searchUseCase.search("山田")).thenReturn(List.of(existing()));

            mockMvc.perform(get("/api/v1/shippers")
                            .param("keyword", "山田")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("山田太郎"));
        }
    }

    /** 解析はできるが検証に落ちる本文（必須項目が空）。 */
    private static final String INVALID_BODY = """
            {
              "type": "INDIVIDUAL",
              "name": "",
              "email": "",
              "address": "",
              "registerAnyway": false
            }
            """;

    @Nested
    @DisplayName("担当外のロールでは")
    class AsOtherRole {

        /**
         * 認可を外すと赤になる形の検証。ロールを 1 つ 1 つ名指しするのは、
         * 「営業以外」を否定形で書くと新しいロールが増えたときに素通りするため。
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "ROLE_SHIPPER", "ROLE_ROUTING", "ROLE_HANDLER", "ROLE_TRACKER",
            "ROLE_ACCOUNTANT", "ROLE_ADMIN"
        })
        @DisplayName("登録は 403 で拒否し、ユースケースを呼ばない")
        void rejectsRegistration(String role) throws Exception {
            mockMvc.perform(post("/api/v1/shippers")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());

            verify(useCase, never()).register(any());
            verify(useCase, never()).registerAnyway(any());
        }


        /**
         * 実環境で見つかった欠陥の回帰（IT3 レビュー）。
         *
         * <p>{@code @Valid} は引数の解決時に走るため、権限の無い呼び出しでも本文が不正なら
         * 400 が返っていた。本人には「この操作はできない」ではなく「入力を直せ」と伝わり、
         * 権限が無いはずの相手にエンドポイントの入力仕様を教えることになる。
         * 直した場所ではなく、欠陥が起きたこの場所に固定する。
         *
         * <p>本文は<strong>解析はできるが検証に落ちる</strong>ものを使う。解析できない本文は
         * フレームワークが引数を組み立てる前に断るため、認可を先に置いても 400 になる。
         */
        @Test
        @DisplayName("本文が不正でも、権限が無ければ 403")
        void checksPermissionBeforeValidation() throws Exception {
            mockMvc.perform(post("/api/v1/shippers")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_BODY))
                    .andExpect(status().isForbidden());

            verify(useCase, never()).register(any());
            verify(useCase, never()).registerAnyway(any());
        }

        @Test
        @DisplayName("検索も 403 で拒否する")
        void rejectsSearch() throws Exception {
            mockMvc.perform(get("/api/v1/shippers")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ロールのヘッダが無い呼び出しも 403 で拒否する")
        void rejectsRequestWithoutRoles() throws Exception {
            // ヘッダが落ちた呼び出しを通すと、Gateway をすり抜けた経路が全権限を得る
            mockMvc.perform(get("/api/v1/shippers")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("法人契約の入力（US03）")
    class CorporateInput {

        private static final String CORPORATE_BODY = """
                {"type": "CORPORATE", "name": "丸紅商事株式会社", "email": "corp@example.com",
                 "address": "東京都千代田区 1-1-1", "phone": null,
                 "contractNumber": "CN-2026-0001", "discountRatePercent": 12.5,
                 "registerAnyway": false}
                """;

        @Test
        @DisplayName("契約情報を値オブジェクトに変換してユースケースへ渡す")
        void passesContractToUseCase() throws Exception {
            when(useCase.register(any())).thenReturn(new RegistrationOutcome.Registered(existing()));

            mockMvc.perform(post("/api/v1/shippers")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPORATE_BODY))
                    .andExpect(status().isCreated());

            ArgumentCaptor<RegisterShipperCommand> captor =
                    ArgumentCaptor.forClass(RegisterShipperCommand.class);
            verify(useCase).register(captor.capture());
            assertThat(captor.getValue().contract()).isEqualTo(new CorporateContract(
                    ContractNumber.of("CN-2026-0001"),
                    DiscountRate.ofPercent(new BigDecimal("12.5"))));
        }

        @Test
        @DisplayName("範囲外の割引率は 400 で理由を返す")
        void rejectsOutOfRangeDiscountRate() throws Exception {
            mockMvc.perform(post("/api/v1/shippers")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPORATE_BODY.replace("12.5", "30.1")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("割引率は 0〜30")));

            // 理由を返さずに 400 だけにすると、営業担当者は何を直せばよいか分からない
            verify(useCase, never()).register(any());
        }
    }

    @Nested
    @DisplayName("荷主の編集（US02 / #550）")
    class Editing {

        private static final String EDIT_BODY = """
                {"type": "INDIVIDUAL", "name": "山田花子", "email": "hanako@example.com",
                 "address": "神奈川県横浜市 2-2-2", "phone": "045-000-0000",
                 "registerAnyway": false}
                """;

        @Test
        @DisplayName("営業担当者が内容を直すと 200 と直した荷主を返す")
        void edits() throws Exception {
            when(editUseCase.edit(any(), any(), any())).thenReturn(Optional.of(existing()));

            mockMvc.perform(put("/api/v1/shippers/1")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EDIT_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shipperCode").value("SHP-000001"));

            ArgumentCaptor<ShipperProfile> captor = ArgumentCaptor.forClass(ShipperProfile.class);
            verify(editUseCase).edit(org.mockito.ArgumentMatchers.eq(1L), captor.capture(), any());
            assertThat(captor.getValue())
                    .isEqualTo(ShipperProfile.of("山田花子", "hanako@example.com",
                            "神奈川県横浜市 2-2-2", "045-000-0000"));
        }

        @Test
        @DisplayName("荷主 1 件を取れる（編集画面を URL で直接開けるようにするため）")
        void findsOne() throws Exception {
            when(searchUseCase.findById(1L)).thenReturn(Optional.of(existing()));

            mockMvc.perform(get("/api/v1/shippers/1")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("山田太郎"));
        }

        @Test
        @DisplayName("居ない荷主を取ろうとすると 404")
        void findMissing() throws Exception {
            when(searchUseCase.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/shippers/999")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("担当外のロールでは 1 件取得も 403")
        void rejectsFindByOtherRole() throws Exception {
            mockMvc.perform(get("/api/v1/shippers/1")
                            .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER"))
                    .andExpect(status().isForbidden());

            verify(searchUseCase, never()).findById(any());
        }

        /**
         * 種別の変更要求を黙って無視すると、原因と無関係な 400 が返る。
         *
         * <p>法人に個人（契約情報なし）を送ると、集約は既存の種別で検査するため
         * 「法人荷主には契約番号が必要です」になり、利用者は何度契約番号を直しても通らない。
         */
        @Test
        @DisplayName("種別を変えようとすると、その理由で 400")
        void rejectsTypeChange() throws Exception {
            when(searchUseCase.findById(1L)).thenReturn(Optional.of(corporate()));

            mockMvc.perform(put("/api/v1/shippers/1")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EDIT_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("種別は変更できません")));

            verify(editUseCase, never()).edit(any(), any(), any());
        }

        @Test
        @DisplayName("居ない荷主を直そうとすると 404")
        void reportsMissing() throws Exception {
            when(editUseCase.edit(any(), any(), any())).thenReturn(Optional.empty());

            mockMvc.perform(put("/api/v1/shippers/999")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EDIT_BODY))
                    .andExpect(status().isNotFound());
        }

        /**
         * 検査の順序を、登録と同じくこの入口でも固定する。認可を先に置かないと、
         * 権限の無い相手にエンドポイントの入力仕様を教えることになる。
         */
        @Test
        @DisplayName("本文が不正でも、権限が無ければ 403")
        void checksPermissionBeforeValidation() throws Exception {
            mockMvc.perform(put("/api/v1/shippers/1")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_BODY))
                    .andExpect(status().isForbidden());

            verify(editUseCase, never()).edit(any(), any(), any());
        }

        @Test
        @DisplayName("入力が不正なら 400 で理由を返す")
        void rejectsInvalidInput() throws Exception {
            mockMvc.perform(put("/api/v1/shippers/1")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_BODY))
                    .andExpect(status().isBadRequest());

            verify(editUseCase, never()).edit(any(), any(), any());
        }
    }
}
