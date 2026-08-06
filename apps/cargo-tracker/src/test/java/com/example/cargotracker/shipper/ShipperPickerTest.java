package com.example.cargotracker.shipper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 荷主選択モーダル（IT2 持ち越し C3）。
 *
 * <p>貨物予約登録から荷主コードを調べる導線。**別タブで一覧を開かせると画面を往復し、
 * 荷主コードを書き写す手間が残る**（`ui_design.md`）。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "sales", roles = "SALES")
class ShipperPickerTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String shipperCode;
    private String name;

    @BeforeEach
    void 荷主を用意する() {
        UUID id = UUID.randomUUID();
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        shipperCode = "SHP-%06d".formatted(seq);
        name = "モーダル検証-" + id;
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', ?, ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """,
                id, shipperCode, name, "picker-%s@example.com".formatted(id));
    }

    @Test
    void 荷主コードと荷主名を返す() throws Exception {
        mockMvc.perform(get("/shippers/picker").param("keyword", name))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(shipperCode)))
                .andExpect(content().string(Matchers.containsString(name)));
    }

    /**
     * <strong>フラグメントだけを返す。</strong> レイアウト一式を返すと、
     * モーダルの中にヘッダとナビゲーションが二重に描画される。
     */
    @Test
    void ページ全体ではなくフラグメントを返す() throws Exception {
        mockMvc.perform(get("/shippers/picker"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("<nav"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("navbar"))));
    }

    @Test
    void 条件に一致しなければその旨を返す() throws Exception {
        mockMvc.perform(get("/shippers/picker").param("keyword", "存在しない荷主" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("条件に一致する荷主がありません")));
    }

    /**
     * {@code /shippers/picker} が荷主 ID として解釈されない。
     *
     * <p>リテラルのパスは {@code /{shipperId}} より優先されるが、**優先順位に頼るなら
     * 頼っていることをテストで示す。** 順序が変われば 404 になる。
     */
    @Test
    void 選択画面のパスは荷主IDとして解釈されない() throws Exception {
        mockMvc.perform(get("/shippers/picker")).andExpect(status().isOk());
    }

    /** 予約登録画面にモーダルを開く導線がある。 */
    @Test
    void 予約登録画面から荷主を検索できる() throws Exception {
        mockMvc.perform(get("/bookings/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("荷主を検索")))
                .andExpect(content().string(Matchers.containsString("/shippers/picker")))
                .andExpect(content().string(Matchers.containsString("shipperPicker")));
    }

    /** 権限の無いロールはモーダルの中身も取れない。**導線を消すだけでは足りない。** */
    @Test
    @WithMockUser(username = "handler", roles = "HANDLER")
    void 権限のないロールはpickerを開けない() throws Exception {
        mockMvc.perform(get("/shippers/picker")).andExpect(status().isForbidden());
    }
}
