package com.example.cargotracker.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * ナビゲーションと案内が、利用者に矛盾を見せないこと（IT13 で発見）。
 *
 * <p><strong>「開けるか」と「読める形で並んでいるか」は別の検査である。</strong>
 * ロール別の到達性テストはリンクの存在しか見ないため、
 * 次の 2 つを判別しない。
 *
 * <ul>
 *   <li><strong>項目が縦に積まれる</strong> — {@code navbar-nav} に {@code <li>} が
 *       混入すると、そこから下が 1 つの塊になる</li>
 *   <li><strong>入口があるのに「機能はありません」と併記される</strong> —
 *       案内の条件はロールを列挙する形であり、入口を足すたびに書き足し忘れる</li>
 * </ul>
 *
 * <p>後者は US34 で荷主に予約を開いたときに一度露見し、
 * <strong>IT13 で経理担当者に請求の入口を足したときに再発した（2 回目）</strong>。
 */
@AutoConfigureMockMvc
@DisplayName("ナビゲーションと案内の整合（IT13）")
class NavigationConsistencyTest extends PostgreSQLIntegrationTestBase {

    /**
     * <strong>ナビゲーションの項目が横一列に並ぶ</strong>（IT13 で発見）。
     *
     * <p>{@code navbar-nav} は flex コンテナであり、<strong>直下の要素が
     * 横に並ぶ</strong>。ところが IT12 で {@code <ul>} の無い {@code <li>} が
     * 混入し、<strong>そこから下の項目が 1 つの塊として縦に積まれていた</strong>
     * （訂正・取り消し、請求対象、請求管理、アカウント管理）。
     *
     * <p><strong>ロール別の到達性テストは、この崩れを判別しない。</strong>
     * リンクは存在するので緑になる。<strong>「開けるか」と「読める形で並んでいるか」は
     * 別の検査である。</strong>
     *
     * <p>見た目そのものは自動では測れないが、<strong>崩れの原因になる構造</strong>
     * （リストでないところに置かれた {@code <li>}）は検出できる。
     */
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void ナビゲーションにリスト構造が混入していない() throws Exception {
        // **navbar-nav の中に <li> を置くと、そこから下が縦に積まれる。**
        // <ul> を伴わない <li> は構造の誤りである。
        // **`<li` だけで探すと `<link` を拾う** — 開始タグの形で見る
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("<li>"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("<li "))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("</li>"))));
    }

    /**
     * <strong>入口があるロールに「利用できる機能はありません」を出さない</strong>
     * （IT13 で発見）。
     *
     * <p>カードが並んでいるのに「あなたの担当業務は今後の提供で対応します」と
     * 書いてあると、<strong>どちらが本当か分からない</strong>。
     * この案内は<strong>入口が 1 つも無いロールにだけ</strong>出す。
     *
     * <p>案内の条件はロールを列挙する形であり、<strong>ロールに入口を足したときに
     * 除外を書き足し忘れる</strong>。US34 で荷主に予約を開いたときに一度露見し、
     * IT13 で経理担当者に請求の入口を足したときに再発した。
     */
    @Test
    @WithMockUser(username = "billing", roles = {"BILLING"})
    void 経理担当者に利用できる機能が無いと案内しない() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                // 入口はある（請求対象のカード）
                .andExpect(content().string(
                        Matchers.containsString("未請求の引取済貨物")))
                // それなのに「機能はありません」と併記しない
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("現在ご利用いただける機能はありません"))));
    }

    /**
     * <strong>入口を持つロールすべてで、案内と入口が同時に出ない</strong>（IT13）。
     *
     * <p>1 ロールずつ確かめると、<strong>次にロールを足したときにまた漏れる</strong>。
     * 「入口があるのに『機能はありません』と出ていないか」を全ロールで機械的に見る。
     *
     * <p><strong>入口が無いロールでは案内が出ること</strong>も対で見る。
     * 一律に案内を消す実装では緑にならない（白紙のダッシュボードは
     * 「権限が付いていない」と受け取られる）。
     */
    @Test
    void 入口があるロールに案内を出さず入口が無いロールには出す() throws Exception {
        // **名簿を書き写さない**（IT13 レビュー C16）。ここに並べ直すと、
        // 画面・テストの 2 か所を同時に直す作業が生まれ、片方が古くなる
        for (String role : com.example.cargotracker.shared.infrastructure.web
                .DashboardEntryRoles.ROLES) {
            String html = mockMvc.perform(get("/")
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors
                                    .user("u-" + role).roles(role)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(html)
                    .as("%s には入口があるのに「機能はありません」と併記している", role)
                    .doesNotContain("現在ご利用いただける機能はありません");
        }

        // **入口が無いロールでは案内が出る。** 一律に消す実装で緑にしない
        String html = mockMvc.perform(get("/")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors
                                .user("u-none").roles("NOBODY")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html)
                .as("入口が無いロールに白紙を見せない")
                .contains("現在ご利用いただける機能はありません");
    }

    /**
     * <strong>カードを足したら名簿にも載っている</strong>（IT13 レビュー C16）。
     *
     * <p>上の検査は<strong>名簿に載っているロール</strong>しか回さない。
     * カードだけ足して名簿に足し忘れると、そのロールは一度も検査されないまま
     * 「入口があるのに『機能はありません』」を出す。<strong>3 回目はここで止める。</strong>
     *
     * <p>画面の {@code sec:authorize} に現れるロールを読み出し、
     * {@code DashboardEntryRoles} に載っているかを突き合わせる。
     */
    @Test
    void 画面のカードのロールはすべて名簿に載っている() throws java.io.IOException {
        String template = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/templates/dashboard.html"));

        java.util.Set<String> inTemplate = new java.util.TreeSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("hasRole\\('([A-Z_]+)'\\)|hasAnyRole\\(([^)]*)\\)")
                .matcher(template);
        while (matcher.find()) {
            String found = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            for (String part : found.split(",")) {
                String role = part.replace("'", "").strip();
                if (!role.isEmpty()) {
                    inTemplate.add(role);
                }
            }
        }

        org.assertj.core.api.Assertions.assertThat(inTemplate)
                .as("**カードのロールを読み出せていない**（検査が空振りしていないか）")
                .isNotEmpty();
        org.assertj.core.api.Assertions.assertThat(
                        com.example.cargotracker.shared.infrastructure.web
                                .DashboardEntryRoles.ROLES)
                .as("**カードを足したら DashboardEntryRoles にも足す。**"
                        + " 忘れると、そのロールに「機能はありません」と出る")
                .containsAll(inTemplate);
    }
}
