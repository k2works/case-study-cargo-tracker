package com.example.trackingms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 利用者ヘッダの検査が<strong>働くこと</strong>を確かめる（[ADR-007]）。
 *
 * <p>登録の字面があっても、除外パスを広げれば業務エンドポイントは開く。安全装置は
 * 「入れたこと」ではなく「働くこと」で確かめる。
 *
 * <p><strong>trackingms は除外を 1 本持つ唯一のサービスである</strong>（US18-5・
 * [ADR-024] 決定 5）。だからこそ<strong>両方向で確かめる</strong>——公開の照会は開き、
 * それ以外は閉じる。片方だけ見ると、除外を広げたことに気づけない。
 */
@AutoConfigureMockMvc
@DisplayName("利用者ヘッダの検査（ADR-007）")
class AuthenticatedUserHeaderRequiredTest extends TrackingIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    /**
     * <strong>業務の入口は、名乗らないと通らない。</strong>
     *
     * <p>401 であって 403 ではない——認可の前で断っている。
     */
    @Test
    @DisplayName("名乗らない業務 API は 401")
    void rejectsBusinessApiWithoutTheHeader() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/manage/TRK-20260823-0001"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tracking/manage/exception-types"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/shipper/tracking"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/tracking/manage")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * <strong>公開の照会だけは、名乗らなくても通る</strong>（US18-5）。
     *
     * <p>荷主はログインしない。ここが閉じていると、追跡照会そのものが成り立たない。
     *
     * <p>404 が返るのは追跡番号が無いためで、<strong>認証で断られていない</strong>ことが
     * ここで見たいことである。
     */
    @Test
    @DisplayName("公開の追跡照会は、名乗らなくても通る")
    void allowsThePublicLookupWithoutTheHeader() throws Exception {
        mockMvc.perform(get("/api/v1/public/tracking/TRK-20260823-9999"))
                .andExpect(status().isNotFound());
    }

    /**
     * <strong>除外は接頭辞の 1 本だけである。</strong>
     *
     * <p>{@code /api/v1/public/} で始まらないものが公開になっていないことを、
     * 紛らわしい形で確かめる——「public を含む」で判定していると、これが通ってしまう。
     */
    @Test
    @DisplayName("public を含むだけの業務パスは開かない")
    void doesNotOpenPathsThatMerelyContainPublic() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/manage/public"))
                .andExpect(status().isUnauthorized());
    }
}
