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

import com.example.bookingms.application.internal.RegisterShipperCommand;
import com.example.bookingms.domain.model.ContractNumber;
import com.example.bookingms.domain.model.CorporateContract;
import com.example.bookingms.domain.model.DiscountRate;
import java.math.BigDecimal;
import org.mockito.ArgumentCaptor;
import com.example.bookingms.application.internal.RegisterShipperUseCase;
import com.example.bookingms.application.internal.SearchShipperUseCase;
import com.example.bookingms.application.internal.RegistrationOutcome;
import com.example.bookingms.domain.model.Shipper;
import com.example.bookingms.domain.model.ShipperType;
import com.example.shared.auth.AuthenticatedUser;
import java.util.List;
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
}
