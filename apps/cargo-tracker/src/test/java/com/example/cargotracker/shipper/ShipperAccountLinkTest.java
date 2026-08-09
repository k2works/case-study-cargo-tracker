package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 荷主詳細で「この荷主でログインできる利用者」を確認する（ADR-013 / IT12 の C11）。
 *
 * <p><strong>営業担当者が案内先を答えられない。</strong> ADR-013 は紐付けを
 * {@code users.shipper_id} に置いたが、<strong>設定されているかを画面から
 * 確かめる手段が無い</strong>まま 3 イテレーション繰り越した（IT9 レビュー M10）。
 * 荷主から「自分で予約を見たい」と言われた営業担当者は、
 * すでに使えるのかどうかを答えられない。
 *
 * <p><strong>紐付けが無いことを空欄にしない。</strong> 空欄は「まだ調べていない」とも
 * 「設定が無い」とも読める。<strong>次に何をすればよいか</strong>まで書く
 * （IT9 のふりかえり T2「気づく手段を作ったら、そこから次の行動へ行けるかを確かめる」）。
 *
 * <p><strong>読み取り専用である。</strong> 紐付けの設定は運用手順で行う
 * （ADR-013 が受け入れた代償）。画面で確認できることと設定できることは別である。
 */
@AutoConfigureMockMvc
@DisplayName("荷主詳細の利用者紐付け表示（C11）")
class ShipperAccountLinkTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID 荷主(String name) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street, contract_number, discount_rate)
                VALUES (?, ?, 'CORPORATE', ?, ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1',
                        ?, 0.1000)
                """, shipperId, "SHP-%06d".formatted(seq), name,
                "link-%d@example.com".formatted(seq), "CT-2026-%04d".formatted(seq));
        return shipperId;
    }

    private void 利用者を紐付ける(String username, UUID shipperId) {
        jdbcTemplate.update("""
                INSERT INTO users (username, email, password, enabled, shipper_id)
                VALUES (?, ?, '{noop}password', TRUE, ?)
                """, username, username + "@example.com", shipperId);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role) VALUES (?, 'ROLE_SHIPPER')", userId);
    }

    private String 荷主詳細(UUID shipperId) throws Exception {
        return mockMvc.perform(get("/shippers/{id}", shipperId)
                        .with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 受入基準: 紐付いた利用者アカウントが読める。 */
    @Test
    void 紐付いた利用者が読める() throws Exception {
        UUID shipperId = 荷主("紐付けテスト株式会社");
        利用者を紐付ける("shipper-link-a", shipperId);

        assertThat(荷主詳細(shipperId))
                .contains("ログインできる利用者")
                .contains("shipper-link-a");
    }

    /**
     * <strong>紐付けが無いことを、次の行動まで含めて伝える。</strong>
     *
     * <p>これが無いと、紐付けが有るときだけ出す実装で緑になる。
     * その実装だと<strong>営業担当者は「無い」のか「表示していないだけ」なのかを
     * 判別できない</strong>。
     */
    @Test
    void 紐付けが無いときは案内先まで読める() throws Exception {
        UUID shipperId = 荷主("未紐付け株式会社");

        assertThat(荷主詳細(shipperId))
                .contains("ログインできる利用者")
                .contains("ありません")
                .contains("システム管理担当");
    }

    /**
     * <strong>他の荷主の利用者を混ぜない。</strong>
     *
     * <p>全件を出す実装でも上の 1 件は緑になる。
     * その実装だと<strong>荷主詳細に他社の利用者名が並ぶ</strong>。
     */
    @Test
    void 他の荷主の利用者は出ない() throws Exception {
        UUID shipperA = 荷主("A 商事");
        UUID shipperB = 荷主("B 物産");
        利用者を紐付ける("shipper-link-b", shipperA);

        assertThat(荷主詳細(shipperB)).doesNotContain("shipper-link-b");
    }

    /**
     * <strong>読み取り専用である。</strong>
     *
     * <p>紐付けの設定は運用手順で行う（ADR-013 の代償）。画面に操作を置くと、
     * <strong>手順書と画面のどちらが正なのかが分からなくなる</strong>。
     */
    @Test
    void 荷主詳細から紐付けを変更できない() throws Exception {
        UUID shipperId = 荷主("読み取り専用株式会社");
        利用者を紐付ける("shipper-link-c", shipperId);

        assertThat(荷主詳細(shipperId)).doesNotContain("/shipper-link");
    }
}
