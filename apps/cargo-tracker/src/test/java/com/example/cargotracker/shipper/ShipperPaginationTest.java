package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperQueryService;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 一覧のページ送りが実際に働くことを検証する（IT2 持ち越し C1）。
 *
 * <p><strong>「ページネーションを入れた」ことと「21 件目が 2 ページ目に出る」ことは別である。</strong>
 * SQL に LIMIT / OFFSET を書いても、総件数を全件取得して数えていれば
 * 一覧を開くたびに全件がメモリに載る。境界の計算は {@code PageTest} が担い、
 * ここでは**実 DB で SQL が絞り込んでいること**を見る。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "sales", roles = "SALES")
class ShipperPaginationTest extends PostgreSQLIntegrationTestBase {

    /**
     * テストメソッドごとの目印。
     *
     * <p><strong>テスト間のデータ独立性は各テストが担保する</strong>
     * （{@code PostgreSQLIntegrationTestBase}）。目印を共有すると、
     * 先に走ったテストが作った荷主が件数に混ざり、**実行順によって落ちる**。
     */
    private String marker;

    @org.junit.jupiter.api.BeforeEach
    void 目印を用意する() {
        marker = "ページ送り検証-" + UUID.randomUUID();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShipperQueryService queryService;

    private void 荷主を作る(int count) {
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            Long seq = jdbcTemplate.queryForObject(
                    "SELECT nextval('shipper_code_seq')", Long.class);
            jdbcTemplate.update("""
                    INSERT INTO shipper (
                        id, shipper_code, shipper_type, name, email, phone,
                        address_country, address_postal_code, address_region,
                        address_city, address_street)
                    VALUES (?, ?, 'INDIVIDUAL', ?, ?, '06-1234-5678',
                            'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                    """,
                    id, "SHP-%06d".formatted(seq), marker + i,
                    "paging-%s@example.com".formatted(id));
        }
    }

    @Test
    void 件数が21なら2ページに分かれる() {
        荷主を作る(21);

        var first = queryService.search(marker, PageRequest.of(1));
        var second = queryService.search(marker, PageRequest.of(2));

        assertThat(first.totalItems()).isEqualTo(21);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).as("1 ページは 20 件までである").hasSize(20);
        assertThat(second.items()).hasSize(1);
    }

    /**
     * <strong>1 ページ目と 2 ページ目に同じ荷主が現れない。</strong>
     *
     * <p>並び順が一意でないと OFFSET の結果が安定せず、**同じ行が両方のページに出たり
     * どのページにも出なかったりする**。荷主コードは一意であり、これが並び順の土台になる。
     */
    @Test
    void ページ間で重複も欠落もしない() {
        荷主を作る(25);

        var first = queryService.search(marker, PageRequest.of(1));
        var second = queryService.search(marker, PageRequest.of(2));

        var codes = first.items().stream().map(v -> v.shipperCode()).toList();
        var secondCodes = second.items().stream().map(v -> v.shipperCode()).toList();

        assertThat(codes).doesNotContainAnyElementsOf(secondCodes);
        assertThat(codes.size() + secondCodes.size()).isEqualTo(25);
    }

    /** 範囲外のページでも 500 にしない。URL を直接編集しただけで壊れない。 */
    @Test
    void 存在しないページを指定しても空の一覧を返す() {
        荷主を作る(1);

        var page = queryService.search(marker, PageRequest.of(999));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void 画面にページ送りが表示される() throws Exception {
        荷主を作る(21);

        mockMvc.perform(get("/shippers").param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("次へ")))
                .andExpect(content().string(Matchers.containsString("全 21 件")));
    }

    /**
     * ページを送っても絞り込み条件が消えない。
     *
     * <p>**2 ページ目に行ったら検索条件がリセットされる**のは、一覧で最も苛立つ壊れ方である。
     */
    @Test
    void ページ送りのリンクが絞り込み条件を引き継ぐ() throws Exception {
        荷主を作る(21);

        mockMvc.perform(get("/shippers").param("keyword", marker))
                .andExpect(content().string(Matchers.containsString("keyword=")))
                .andExpect(content().string(Matchers.containsString("page=2")));
    }
}
