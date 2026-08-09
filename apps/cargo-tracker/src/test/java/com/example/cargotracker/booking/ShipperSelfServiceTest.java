package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * US34: 荷主が自社の予約を照会する。受入基準に 1:1 で対応させる。
 *
 * <p><strong>本テストの主眼は「見えること」より「見えないこと」にある。</strong>
 * IT2 で貨物予約一覧を荷主に開放したとき、利用者アカウントと荷主を結びつける手段が無く、
 * <strong>他社の予約まで見える状態だった</strong>。クローズ前のレビューで気づいて開放を
 * 取り消し、以来 7 イテレーションにわたって「US34 で紐付けを作ってから開放する」と
 * 書き続けてきた。
 *
 * <p><strong>絞り込みは SQL で行う。</strong> 画面側で絞ると、検索条件・ページング・
 * 並べ替えのどれか 1 つを変えたときに漏れる。
 */
@AutoConfigureMockMvc
@DisplayName("US34 荷主が自社の予約を照会する")
class ShipperSelfServiceTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    /** 自社（ログインする荷主）の荷主 ID。 */
    private UUID ownShipper;

    /** 他社。**この荷主の予約が 1 件でも現れたら失敗である。** */
    private UUID otherShipper;

    private UUID ownBooking;
    private UUID otherBooking;

    @BeforeEach
    void 荷主とその予約を用意する() {
        ownShipper = 荷主を登録する("自社物産", "self");
        otherShipper = 荷主を登録する("他社商事", "other");
        ownBooking = 予約を登録する(ownShipper, "JPOSA", "USLAX");
        otherBooking = 予約を登録する(otherShipper, "JPKIX", "SGSIN");

        // 利用者 shipper を自社に紐づける（US34 の中核）
        jdbcTemplate.update(
                "UPDATE users SET shipper_id = ? WHERE username = 'shipper'", ownShipper);
    }

    /**
     * <strong>共有の {@code users} 行を元に戻す</strong>（IT9 レビュー M12 の返済）。
     *
     * <p>荷主・予約はテストごとに新しく作るが、<strong>{@code users} は
     * マイグレーションが用意した共有の行</strong>である。紐付けを残したまま終わると、
     * 後続のテストが「この利用者は荷主に紐づいている」状態で走る。
     *
     * <p>壊れるのは<strong>このテストではなく別のテスト</strong>であり、しかも
     * 実行順に依存する。単体で走らせると再現しないため、原因にたどり着きにくい。
     */
    @AfterEach
    void 利用者の紐付けを元に戻す() {
        jdbcTemplate.update("UPDATE users SET shipper_id = NULL WHERE username = 'shipper'");
    }

    private UUID 荷主を登録する(String name, String prefix) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', ?, ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, id, "SHP-%06d".formatted(seq), name, "%s-%d@example.com".formatted(prefix, seq));
        return id;
    }

    private UUID 予約を登録する(UUID shipperId, String origin, String destination) {
        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status)
                VALUES (?, ?, 'GENERAL', 1000.000, ?, ?, ?, 'PRELIMINARY', 'NOT_ROUTED')
                """, bookingId, shipperId, origin, destination,
                LocalDate.now(clock).plusDays(40));
        return bookingId;
    }

    /** 紐付けを持つ荷主としてリクエストする（**補助は 1 つだけ置く**）。 */
    private RequestPostProcessor 荷主として(UUID shipperId) {
        return com.example.cargotracker.support.ShipperScopedTestUser.scopedTo(shipperId);
    }

    /** 受入基準: 荷主は**自社の予約のみ**を一覧で確認できる。 */
    @Test
    void 荷主の一覧には自社の予約だけが並ぶ() throws Exception {
        String body = mockMvc.perform(get("/bookings").with(荷主として(ownShipper)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(ownBooking.toString().substring(0, 8));
        // **他社の予約が 1 件でも現れたら失敗である**
        assertThat(body).doesNotContain(otherBooking.toString().substring(0, 8));
        assertThat(body).doesNotContain("他社商事");
    }

    /**
     * <strong>検索条件で他社を指定しても増えない。</strong>
     *
     * <p>絞り込みを画面側で行うと、検索条件を変えたときに漏れる。
     * **利用者が指定できる条件は、絞り込みの後に効く**のでなければならない。
     */
    @Test
    void 検索条件で他社を指定しても現れない() throws Exception {
        String body = mockMvc.perform(get("/bookings").with(荷主として(ownShipper))
                        .param("origin", "JPKIX")
                        .param("destination", "SGSIN"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(otherBooking.toString().substring(0, 8));
    }

    /**
     * 受入基準: <strong>他社の予約は URL を直接指定しても開けない。</strong>
     *
     * <p><strong>404 を返す</strong>（403 ではない）。403 は「存在するが見せない」と
     * 伝えてしまい、番号を変えながら叩けば他社の予約の有無を確かめられる。
     * 追跡照会（US18）で「存在しない番号と権限外の番号を区別しない」と決めたのと同じ判断である。
     */
    @Test
    void 他社の予約詳細は404になる() throws Exception {
        mockMvc.perform(get("/bookings/{id}", otherBooking).with(荷主として(ownShipper)))
                .andExpect(status().isNotFound());
    }

    /**
     * <strong>404 でも日本語の画面を出す。</strong>
     *
     * <p>英語の Whitelabel Error Page を見せると、利用者は障害だと受け取り
     * 情シスへの問い合わせになる。**「無い」と「見せない」は書き分けない** —
     * 書き分けると、番号を変えながら叩けば他社のデータの有無を確かめられる。
     */
    @Test
    void 見つからない画面は日本語で案内する() throws Exception {
        // **ブラウザとして開く。** Accept を付けないと JSON のエラー表現が返り、
        // 利用者が実際に見る画面を確かめたことにならない
        String body = mockMvc.perform(get("/error")
                        .accept(org.springframework.http.MediaType.TEXT_HTML)
                        .requestAttr("jakarta.servlet.error.status_code", 404)
                        .with(荷主として(ownShipper)))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("お探しのものが見つかりません");
        assertThat(body).doesNotContain("Whitelabel");
    }

    /** 自社の予約詳細は開ける。 */
    @Test
    void 自社の予約詳細は開ける() throws Exception {
        mockMvc.perform(get("/bookings/{id}", ownBooking).with(荷主として(ownShipper)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("自社物産")));
    }

    /**
     * <strong>荷主が予約詳細から行き止まりにならない</strong>（IT11 / C8）。
     *
     * <p>荷主が開ける先は自社の予約一覧（{@code /bookings} は荷主には自社分だけを
     * 返す）と貨物追跡である。詳細に戻り先が無いと、ブラウザの戻るボタンしか
     * 手が無い。<strong>「一覧に戻る」は営業だけに出していた</strong>ため、
     * 開ける先があるのに荷主だけ導線が消えていた。
     */
    @Test
    void 荷主は予約詳細から自社の一覧へ戻れる() throws Exception {
        mockMvc.perform(get("/bookings/{id}", ownBooking).with(荷主として(ownShipper)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("一覧に戻る")));
    }

    /**
     * <strong>追跡番号が出ている予約からは、貨物追跡へ行ける</strong>（C8。状態軸）。
     *
     * <p>荷主が予約詳細でいちばん知りたいのは「いまどこにあるか」である。
     * 追跡番号を読み取って別の画面で打ち直させない。
     */
    @Test
    void 荷主は追跡番号のある予約から貨物追跡へ行ける() throws Exception {
        jdbcTemplate.update(
                "UPDATE cargo SET tracking_number = ? WHERE booking_id = ?",
                "TRK-20261101-9601", ownBooking);

        mockMvc.perform(get("/bookings/{id}", ownBooking).with(荷主として(ownShipper)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.containsString("/tracking/TRK-20261101-9601")));
    }

    /**
     * <strong>荷主が自社の予約で通知履歴を読める</strong>（IT11 / C20）。
     *
     * <p>通知は ADR-006 により記録で満たしている。その記録を
     * <strong>受け取る当人が読めなければ、通知したことにならない</strong>。
     * IT10 までは営業担当者にしか表示していなかった。
     *
     * <p>他社の予約はそもそも 404 になるため、開放しても自社分しか見えない。
     */
    @Test
    void 荷主は自社の予約で通知履歴を読める() throws Exception {
        mockMvc.perform(get("/bookings/{id}", ownBooking).with(荷主として(ownShipper)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("通知履歴")));
    }

    /**
     * 受入基準: <strong>紐づけのない荷主アカウントでは、予約が 1 件も表示されない。</strong>
     *
     * <p>**「紐付けが無い＝全部見える」にしない。** 設定漏れが情報漏洩に直結する形を作らない。
     */
    @Test
    @WithMockUser(username = "consignee", roles = "SHIPPER")
    void 紐づけの無い荷主アカウントでは何も表示されない() throws Exception {
        String body = mockMvc.perform(get("/bookings"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(ownBooking.toString().substring(0, 8));
        assertThat(body).doesNotContain(otherBooking.toString().substring(0, 8));
        assertThat(body).contains("表示できる予約がありません");
    }

    /**
     * <strong>社内ロールは荷主に紐づかない。</strong>
     *
     * <p>営業担当者はすべての予約を見る。**「全員が荷主に紐づく」形にすると、
     * 社内利用者を作るたびにダミーの荷主が要る。**
     */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者の一覧は絞られない() throws Exception {
        String body = mockMvc.perform(get("/bookings"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(ownBooking.toString().substring(0, 8));
        assertThat(body).contains(otherBooking.toString().substring(0, 8));
    }

    /** 受入基準: 荷主は予約の登録・キャンセルはできない。 */
    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void 荷主は予約を登録できない() throws Exception {
        mockMvc.perform(get("/bookings/new"))
                .andExpect(status().isForbidden());
    }

    /**
     * <strong>登録フォームそのものを開けない。</strong>
     *
     * <p>開けてしまうと、荷主は全項目を入力したあとで 403 に当たる（送信は営業のみ）。
     * **押せない操作を見せない。**
     */
    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void 荷主は予約登録の画面も部品も開けない() throws Exception {
        mockMvc.perform(get("/bookings/new")).andExpect(status().isForbidden());
        // 種別の入力欄を返す部品も同じ扱いにする（フォームの一部である）
        mockMvc.perform(get("/bookings/new/specification").param("cargoType", "HAZARDOUS"))
                .andExpect(status().isForbidden());
    }

    /** 受入基準: 荷主は予約のキャンセルもできない。 */
    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void 荷主は予約をキャンセルできない() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/bookings/{id}/cancel", ownBooking)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    /** <strong>一覧に登録のボタンを出さない。</strong> 押せない操作を見せない。 */
    @Test
    void 荷主の一覧に新規登録のボタンを出さない() throws Exception {
        String body = mockMvc.perform(get("/bookings").with(荷主として(ownShipper)))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("新規予約登録");
    }

    /** <strong>作業入口がある。</strong> ダッシュボードから自社の予約へ行ける（T3）。 */
    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void ダッシュボードから自社の予約へ行ける() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("自社の予約")))
                .andExpect(content().string(Matchers.containsString("/bookings")));
    }
}
