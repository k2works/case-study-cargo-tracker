package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.cargotracker.shipper.application.internal.queryservices.ShipperQueryService;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperView;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** US32: 荷主情報を訂正する。受け入れ基準に 1:1 で対応させる。 */
@AutoConfigureMockMvc
@WithMockUser(username = "sales", roles = "SALES")
class ShipperCorrectionAcceptanceTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShipperQueryService queryService;

    private String 荷主を登録する(String name) {
        UUID id = UUID.randomUUID();
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', ?, ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """,
                id, "SHP-%06d".formatted(seq), name, "edit-%s@example.com".formatted(id));
        return id.toString();
    }

    private Map<String, String> form(ShipperView shipper) {
        Map<String, String> values = new HashMap<>();
        values.put("version", String.valueOf(shipper.version()));
        values.put("name", shipper.name());
        values.put("email", shipper.email());
        values.put("phone", shipper.phone());
        values.put("addressCountry", shipper.addressCountry());
        values.put("addressPostalCode", shipper.addressPostalCode());
        values.put("addressRegion", shipper.addressRegion());
        values.put("addressCity", shipper.addressCity());
        values.put("addressStreet", shipper.addressStreet());
        return values;
    }

    private MockHttpServletRequestBuilder postForm(String id, Map<String, String> values) {
        var req = post("/shippers/{id}/edit", id).with(csrf());
        values.forEach(req::param);
        return req;
    }

    /** 受入基準: 荷主詳細から編集画面を開ける。 */
    @Test
    void 荷主詳細から編集画面を開ける() throws Exception {
        String id = 荷主を登録する("山田太朗");

        mockMvc.perform(get("/shippers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/shippers/" + id + "/edit")));

        mockMvc.perform(get("/shippers/{id}/edit", id))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/edit"));
    }

    /** 受入基準: 荷主名・メール・電話・住所を訂正でき、一覧・詳細に即座に反映される。 */
    @Test
    void 荷主名と連絡先と住所を訂正できる() throws Exception {
        String id = 荷主を登録する("山田太朗");
        var values = form(queryService.findById(id).orElseThrow());
        values.put("name", "山田太郎");
        values.put("phone", "06-9999-0000");
        values.put("addressCity", "大阪市港区");

        mockMvc.perform(postForm(id, values)).andExpect(status().is3xxRedirection());

        var corrected = queryService.findById(id).orElseThrow();
        assertThat(corrected.name()).isEqualTo("山田太郎");
        assertThat(corrected.phone()).isEqualTo("06-9999-0000");
        assertThat(corrected.addressCity()).isEqualTo("大阪市港区");

        mockMvc.perform(get("/shippers"))
                .andExpect(content().string(Matchers.containsString("山田太郎")));
    }

    /**
     * 受入基準: 荷主コードと荷主種別は変更できない。
     *
     * <p>**画面から欄を消すだけでは足りない。** 細工した POST を送っても
     * 変わらないことを確認する。
     */
    @Test
    void 荷主コードと種別は細工した送信でも変わらない() throws Exception {
        String id = 荷主を登録する("山田太朗");
        var before = queryService.findById(id).orElseThrow();
        var values = form(before);
        values.put("shipperCode", "SHP-999999");
        values.put("shipperType", "CORPORATE");

        mockMvc.perform(postForm(id, values)).andExpect(status().is3xxRedirection());

        var after = queryService.findById(id).orElseThrow();
        assertThat(after.shipperCode()).isEqualTo(before.shipperCode());
        assertThat(after.shipperType()).isEqualTo("INDIVIDUAL");
    }

    /** 受入基準: 他の荷主と同じメールに変更しようとした場合、訂正されず既存が提示される。 */
    @Test
    void 他の荷主と同じメールには変更できず既存が提示される() throws Exception {
        String other = 荷主を登録する("既存荷主");
        String target = 荷主を登録する("訂正対象");
        String otherEmail = queryService.findById(other).orElseThrow().email();

        var values = form(queryService.findById(target).orElseThrow());
        values.put("email", otherEmail);

        mockMvc.perform(postForm(target, values))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/edit"))
                .andExpect(content().string(Matchers.containsString("既存荷主")));

        assertThat(queryService.findById(target).orElseThrow().email()).isNotEqualTo(otherEmail);
    }

    /**
     * 同時訂正で後勝ちにならない。
     *
     * <p>古いバージョンを持ったまま送信すると、訂正は保存されず警告が出る。
     * **黙って上書きすると、先に訂正した担当者は自分の訂正が消えたことに気づけない。**
     */
    @Test
    void 他の担当者が先に訂正していたら上書きしない() throws Exception {
        String id = 荷主を登録する("山田太朗");
        var stale = form(queryService.findById(id).orElseThrow());

        // 先に別の担当者が訂正する
        var first = form(queryService.findById(id).orElseThrow());
        first.put("name", "先の訂正");
        mockMvc.perform(postForm(id, first)).andExpect(status().is3xxRedirection());

        // 古いバージョンのまま送る
        stale.put("name", "後の訂正");
        mockMvc.perform(postForm(id, stale))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("他の担当者が先に更新しました")));

        assertThat(queryService.findById(id).orElseThrow().name()).isEqualTo("先の訂正");
    }

    @Test
    void メールアドレスの形式が不正なら訂正できない() throws Exception {
        String id = 荷主を登録する("山田太朗");
        var values = form(queryService.findById(id).orElseThrow());
        values.put("email", "invalid-email");

        mockMvc.perform(postForm(id, values))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/edit"));
    }

    /**
     * 受入基準: 誰がいつ何を訂正したかが業務操作ログに記録される。
     *
     * <p><strong>コードに logger.info が書いてあることと、出ていることは別である。</strong>
     * 出力そのものを検証する（IT1 持ち越し C4）。
     */
    @Test
    void 訂正が誰の操作として記録される() throws Exception {
        String id = 荷主を登録する("山田太朗");
        var values = form(queryService.findById(id).orElseThrow());
        values.put("name", "山田太郎");

        try (com.example.cargotracker.support.LogCapture capture =
                     com.example.cargotracker.support.LogCapture.of("audit.shipper")) {
            mockMvc.perform(postForm(id, values)).andExpect(status().is3xxRedirection());

            assertThat(capture.messages())
                    .anySatisfy(message -> assertThat(message)
                            .contains("荷主訂正")
                            .contains(id)
                            .contains("actor=sales")
                            .contains("山田太郎"));
        }
    }

    /** 失敗した訂正は記録されない。**行われなかった操作が残ると監査ログは信用できなくなる。** */
    @Test
    void 重複メールで失敗した訂正は記録されない() throws Exception {
        String other = 荷主を登録する("既存荷主");
        String target = 荷主を登録する("訂正対象");
        var values = form(queryService.findById(target).orElseThrow());
        values.put("email", queryService.findById(other).orElseThrow().email());

        try (com.example.cargotracker.support.LogCapture capture =
                     com.example.cargotracker.support.LogCapture.of("audit.shipper")) {
            mockMvc.perform(postForm(target, values)).andExpect(status().isOk());

            assertThat(capture.messages()).noneMatch(m -> m.contains("荷主訂正"));
        }
    }

    /**
     * 監査ログに改行を差し込めない。
     *
     * <p><strong>利用者が内容を書ける監査ログは、証拠として使えない。</strong>
     * 荷主名に改行を入れると、ログの 1 行を割って「別の操作が行われた」ように
     * 見せる行を後続に足せる（ログインジェクション）。
     */
    @Test
    void 荷主名に改行を入れても監査ログの行を割れない() throws Exception {
        String id = 荷主を登録する("山田太朗");
        var values = form(queryService.findById(id).orElseThrow());
        values.put("name", "正常な荷主\n2026-08-06 荷主訂正 actor=admin 偽の行");

        try (com.example.cargotracker.support.LogCapture capture =
                     com.example.cargotracker.support.LogCapture.of("audit.shipper")) {
            mockMvc.perform(postForm(id, values)).andExpect(status().is3xxRedirection());

            assertThat(capture.messages())
                    .as("ログの 1 件が複数行に割れてはならない")
                    .allSatisfy(message -> assertThat(message).doesNotContain("\n"));
        }
    }

    @Test
    void 存在しない荷主の編集画面は404を返す() throws Exception {
        mockMvc.perform(get("/shippers/{id}/edit", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }
}
