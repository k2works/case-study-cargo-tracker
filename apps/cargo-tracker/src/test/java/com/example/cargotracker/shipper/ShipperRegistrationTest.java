package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/** US02: 荷主を登録する。受け入れ基準に 1:1 で対応させる。 */
@AutoConfigureMockMvc
@WithMockUser(username = "sales", roles = "SALES")
class ShipperRegistrationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private ShipperRepository repository;

    private static java.util.Map<String, String> form(String email) {
        return java.util.Map.of(
                "shipperType", "INDIVIDUAL",
                "name", "山田太郎",
                "email", email,
                "phone", "090-1234-5678",
                "addressCountry", "JP",
                "addressPostalCode", "530-0001",
                "addressRegion", "大阪府",
                "addressCity", "大阪市北区",
                "addressStreet", "梅田 1-1-1");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postForm(
            java.util.Map<String, String> values) {
        var req = post("/shippers").with(csrf());
        values.forEach(req::param);
        return req;
    }

    @Test
    void 個人荷主を登録できる() throws Exception {
        mockMvc.perform(postForm(form("taro@example.com")))
                .andExpect(status().is3xxRedirection())
                // 登録完了後、荷主 ID が発行される（PRG で詳細へ）
                .andExpect(redirectedUrlPattern("/shippers/*"));

        assertThat(repository.findByEmail("taro@example.com")).isPresent();
    }

    @Test
    void 登録すると荷主IDと荷主コードが発行される() throws Exception {
        mockMvc.perform(postForm(form("code@example.com")));

        var shipper = repository.findByEmail("code@example.com").orElseThrow();
        assertThat(shipper.id()).isNotNull();
        assertThat(shipper.shipperCode().value()).matches("SHP-\\d{6}");
    }

    @Test
    void 同一メールアドレスは登録できず既存荷主が提示される() throws Exception {
        mockMvc.perform(postForm(form("dup@example.com")));

        mockMvc.perform(postForm(form("dup@example.com")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("existingShipper"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("既に登録")));
    }

    @Test
    void 必須項目が欠けていると登録できない() throws Exception {
        var invalid = new java.util.HashMap<>(form("invalid@example.com"));
        invalid.remove("addressCity");
        invalid.put("addressCity", "");

        mockMvc.perform(postForm(invalid))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "addressCity"));

        assertThat(repository.findByEmail("invalid@example.com")).isEmpty();
    }

    @Test
    void 登録した荷主を一覧と詳細で確認できる() throws Exception {
        mockMvc.perform(postForm(form("list@example.com")));

        mockMvc.perform(get("/shippers"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("list@example.com")));

        var shipper = repository.findByEmail("list@example.com").orElseThrow();
        mockMvc.perform(get("/shippers/" + shipper.id().value()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("山田太郎")));
    }

    @Test
    void 法人を指定しても個人として黙って登録されない() throws Exception {
        // 法人荷主は US03 で扱う。**受け取れない値を黙って別の値で保存するのが最悪の選択肢**で、
        // 利用者にも記録にも何も残らないまま種別だけが入れ替わる
        mockMvc.perform(post("/shippers").with(csrf()).param("shipperType", "CORPORATE")
                        .param("name", "法人テスト")
                        .param("email", "corporate-reject@example.com")
                        .param("addressCountry", "JP")
                        .param("addressPostalCode", "100-0001")
                        .param("addressRegion", "東京都")
                        .param("addressCity", "千代田区"))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/form"));

        assertThat(repository.findByEmail(new Email("corporate-reject@example.com"))).isEmpty();
    }
}
