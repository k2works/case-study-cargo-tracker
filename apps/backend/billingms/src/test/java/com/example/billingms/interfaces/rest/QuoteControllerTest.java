package com.example.billingms.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.billingms.application.internal.QuoteChargeUseCase;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.contract.ChargeQuoteContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 料金試算の API（US01-3）。**プロバイダ側の契約テスト**。
 *
 * <p>コンシューマ側（bookingms の {@code RestChargeQuoteFinderTest}）が固定した期待を、
 * ここで同じ形で満たす。<strong>本物のユースケースを通す</strong>——モックにすると、
 * 「式が 1 か所にある」ことを確かめたはずの検査が、式を通らないまま緑になる。
 */
@WebMvcTest(QuoteController.class)
@Import(QuoteChargeUseCase.class)
@DisplayName("料金試算の API（サービス間）")
class QuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String BODY = """
            {
              "legs": [{"loadRegion": "DOMESTIC", "unloadRegion": "OCEAN"}],
              "weightKg": 4200,
              "cargoType": "GENERAL"
            }
            """;

    /**
     * <strong>実料金と同じ式を通る</strong>（[ADR-028] 決定 6）。
     *
     * <p>50,000 × 6.0（遠洋 1 区間）× 4.2 × 1.0 = 1,260,000。
     */
    @Test
    @DisplayName("既知のサービスは、基本料金の試算を受け取れる")
    void quotesTheBaseAmount() throws Exception {
        mockMvc.perform(post(ChargeQuoteContract.PATH)
                        .header(AuthenticatedUser.USER_ID_HEADER, "system:bookingms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseAmount.value").value(1260000))
                .andExpect(jsonPath("$.baseAmount.currency").value("JPY"));
    }

    /**
     * <strong>名簿に無い主体は通さない</strong>（[ADR-015] 以来の許可リスト方式）。
     *
     * <p>人のロールでも開かない——経理担当者は請求の画面を使う。
     */
    @ParameterizedTest(name = "caller = {0}")
    @ValueSource(strings = {"system:handlingms", "system:billingms", "accountant01", "sales01"})
    @DisplayName("名簿に無い主体は試算できない")
    void rejectsUntrustedCallers(String caller) throws Exception {
        mockMvc.perform(post(ChargeQuoteContract.PATH)
                        .header(AuthenticatedUser.USER_ID_HEADER, caller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    /** <strong>区間の無い経路は断る。</strong>0 円で返すと、運ぶのに無料で見積もる。 */
    @Test
    @DisplayName("区間が 1 本も無い経路は 400 を返す")
    void rejectsRoutesWithoutLegs() throws Exception {
        mockMvc.perform(post(ChargeQuoteContract.PATH)
                        .header(AuthenticatedUser.USER_ID_HEADER, "system:bookingms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legs": [], "weightKg": 4200, "cargoType": "GENERAL"}
                                """))
                .andExpect(status().isBadRequest());
    }

    /** <strong>知らない地域区分・貨物種別は断る。</strong>既定値に倒すと安く見積もられる。 */
    @Test
    @DisplayName("扱いを決めていない地域区分は 400 を返す")
    void rejectsUnknownRegions() throws Exception {
        mockMvc.perform(post(ChargeQuoteContract.PATH)
                        .header(AuthenticatedUser.USER_ID_HEADER, "system:bookingms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legs": [{"loadRegion": "MOON", "unloadRegion": "OCEAN"}],
                                 "weightKg": 4200, "cargoType": "GENERAL"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
