package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 通関申告の画面と到達性（US29）。
 *
 * <p>ロールごとに<strong>「開けないこと」と「開けること」を対で書く</strong>
 * （IT10 の Try T2）。403 だけを並べると、開けるはずの画面が開けないことに気づけない。
 *
 * <p>港は MYPKG / INNSA を使う。<strong>他のテストも使っているが、本テストは
 * 航海を登録しない</strong>ため、航路の検索結果を押し出すことがない
 * （「使っていない港」と書くのは事実と違う。IT11 レビュー）。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "handler", roles = "HANDLER")
@DisplayName("通関申告の画面（US29）")
class CustomsScreenTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    /**
     * 追跡番号。<strong>発行日を番号に埋め込む形式である</strong>ため、
     * 業務の今日から作る。固定日で書くと「作業日時が追跡番号の発行日より前です」に
     * 当たり、クラス全体が落ちる（絶対日付を書かない）。
     */
    private String 追跡番号(int suffix) {
        // **連番の若い側を使わない。** 追跡番号は「発行日 + その日の連番」であり、
        // 本番の採番は 0001 から始まる。テストが 0001 を先に埋めると、
        // **同じ日に発行するだけの他のテストが UNIQUE 制約で落ちる**
        // （原因でないテストが落ちる形を作らない）
        return "TRK-%s-%04d".formatted(
                java.time.LocalDate.now(clock).toString().replace("-", ""), 9700 + suffix);
    }

    /** 通関の荷役まで済んだ貨物を用意する。 */
    private void 通関待ちの貨物(String trackingNumber) throws Exception {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '通関画面商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq),
                "screen-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number, consignee_name)
                VALUES (?, ?, 'GENERAL', 1000, 'MYPKG', 'INNSA', CURRENT_DATE + 60,
                        'IN_TRANSIT', 'NOT_ROUTED', ?, '受取花子')
                """, bookingId, shipperId, trackingNumber);

        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'UNLOADED', 0)
                """, trackingNumber, bookingId);

        mockMvc.perform(post("/handling")
                .param("trackingNumber", trackingNumber)
                .param("type", "CUSTOMS")
                .param("completionTime",
                        LocalDateTime.now(clock).minusHours(2).withSecond(0).withNano(0).toString())
                .param("locationUnlocode", "INNSA")
                .param("operatorName", "港湾太郎")
                .with(user("handler").roles("HANDLER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private ResultActions 申告を登録する(String trackingNumber, String declarationNumber)
            throws Exception {
        return mockMvc.perform(post("/handling/customs")
                .param("trackingNumber", trackingNumber)
                .param("declarationNumber", declarationNumber)
                .param("declaredAt",
                        LocalDateTime.now(clock).minusHours(1).withSecond(0).withNano(0).toString())
                .with(user("handler").roles("HANDLER")).with(csrf()));
    }

    private long 申告の識別子(String declarationNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM customs_declaration WHERE declaration_number = ?",
                Long.class, declarationNumber);
    }

    @Nested
    @DisplayName("到達性")
    class 到達性 {

        /** 一覧に申告が並ぶ。**追跡番号と状態が読める。** */
        @Test
        void 一覧に申告と状態が並ぶ() throws Exception {
            通関待ちの貨物(追跡番号(1));
            申告を登録する(追跡番号(1), "DEC-9701")
                    .andExpect(status().is3xxRedirection());

            mockMvc.perform(get("/handling/customs"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("DEC-9701")))
                    .andExpect(content().string(Matchers.containsString(追跡番号(1))))
                    .andExpect(content().string(Matchers.containsString("審査中")));
        }

        /** 一覧から登録フォームへ行ける。 */
        @Test
        void 一覧から登録フォームへ行ける() throws Exception {
            mockMvc.perform(get("/handling/customs"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            Matchers.containsString("/handling/customs/new")));
            mockMvc.perform(get("/handling/customs/new"))
                    .andExpect(status().isOk());
        }

        /** 詳細に変更履歴が出る（受入基準）。 */
        @Test
        void 詳細に変更履歴が出る() throws Exception {
            通関待ちの貨物(追跡番号(2));
            申告を登録する(追跡番号(2), "DEC-9702");
            long id = 申告の識別子("DEC-9702");
            mockMvc.perform(post("/handling/customs/{id}/status", id)
                    .param("status", "HELD").param("reason", "書類の不備")
                    .with(user("handler").roles("HANDLER")).with(csrf()));

            mockMvc.perform(get("/handling/customs/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("変更履歴")))
                    .andExpect(content().string(Matchers.containsString("書類の不備")))
                    .andExpect(content().string(Matchers.containsString("handler")));
        }

        /** 一覧を通関状態で絞り込める（受入基準）。 */
        @Test
        void 一覧を通関状態で絞り込める() throws Exception {
            通関待ちの貨物(追跡番号(3));
            申告を登録する(追跡番号(3), "DEC-9703");

            mockMvc.perform(get("/handling/customs").param("status", "CLEARED"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            Matchers.not(Matchers.containsString("DEC-9703"))));
            mockMvc.perform(get("/handling/customs").param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("DEC-9703")));
        }

        /** 一覧を追跡番号で検索できる（受入基準）。 */
        @Test
        void 一覧を追跡番号で検索できる() throws Exception {
            通関待ちの貨物(追跡番号(4));
            申告を登録する(追跡番号(4), "DEC-9704");

            mockMvc.perform(get("/handling/customs").param("q", 追跡番号(4)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("DEC-9704")));
        }

        /**
         * 一覧を<strong>貨物 ID</strong> で検索できる（受入基準）。
         *
         * <p>検索の OR 分岐のうち、追跡番号しか通していなかった
         * （booking_id の行を削除しても赤にならなかった。IT11 レビュー）。
         */
        @Test
        void 一覧を貨物IDで検索できる() throws Exception {
            通関待ちの貨物(追跡番号(9));
            申告を登録する(追跡番号(9), "DEC-9709");
            String bookingId = jdbcTemplate.queryForObject(
                    "SELECT CAST(booking_id AS VARCHAR) FROM cargo WHERE tracking_number = ?",
                    String.class, 追跡番号(9));

            mockMvc.perform(get("/handling/customs").param("q", bookingId))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("DEC-9709")));
        }

        /** <strong>詳細から貨物の追跡へ戻れる</strong>（状態軸の到達性）。 */
        @Test
        void 詳細から追跡へ行ける() throws Exception {
            通関待ちの貨物(追跡番号(5));
            申告を登録する(追跡番号(5), "DEC-9705");

            mockMvc.perform(get("/handling/customs/{id}", 申告の識別子("DEC-9705")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            Matchers.containsString("/tracking/" + 追跡番号(5))));
        }
    }

    @Nested
    @DisplayName("留置の警告")
    class 留置の警告 {

        /**
         * <strong>留置のまま 3 日を超えると一覧で警告される</strong>（受入基準）。
         *
         * <p>留置の起点を直接ずらす。<strong>時間の経過をテストで待たない。</strong>
         */
        @Test
        void 留置が3日を超えると一覧で警告される() throws Exception {
            通関待ちの貨物(追跡番号(6));
            申告を登録する(追跡番号(6), "DEC-9706");
            long id = 申告の識別子("DEC-9706");
            mockMvc.perform(post("/handling/customs/{id}/status", id)
                    .param("status", "HELD").param("reason", "検査中")
                    .with(user("handler").roles("HANDLER")).with(csrf()));

            // まだ警告しない。**まず開けたことと、対象の行が出ていることを見る**
            // （要素の不在は、画面が壊れていても成立する）
            mockMvc.perform(get("/handling/customs").param("q", "DEC-9706"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("DEC-9706")))
                    .andExpect(content().string(
                            Matchers.not(Matchers.containsString("留置が長引いています"))));

            jdbcTemplate.update(
                    "UPDATE customs_declaration SET held_since = ? WHERE id = ?",
                    java.sql.Timestamp.from(clock.instant().minusSeconds(5 * 24 * 3600)), id);

            mockMvc.perform(get("/handling/customs").param("q", "DEC-9706"))
                    .andExpect(content().string(
                            Matchers.containsString("留置が長引いています")));
        }

        /**
         * <strong>追跡管理者のダッシュボードに留置の件数が出る</strong>（受入基準）。
         *
         * <p>件数を 0 に潰しても緑になる形にしない。<strong>登録の前後で差を見る。</strong>
         */
        @Test
        @WithMockUser(username = "tracker", roles = "TRACKER")
        void ダッシュボードの留置件数が増える() throws Exception {
            int before = 留置の件数();

            通関待ちの貨物(追跡番号(7));
            申告を登録する(追跡番号(7), "DEC-9707");
            long id = 申告の識別子("DEC-9707");
            mockMvc.perform(post("/handling/customs/{id}/status", id)
                    .param("status", "HELD").param("reason", "検査中")
                    .with(user("handler").roles("HANDLER")).with(csrf()));
            jdbcTemplate.update(
                    "UPDATE customs_declaration SET held_since = ? WHERE id = ?",
                    java.sql.Timestamp.from(clock.instant().minusSeconds(5 * 24 * 3600)), id);

            assertThat(留置の件数()).isEqualTo(before + 1);
        }

        /** ダッシュボードの HTML から留置の件数を読む。 */
        private int 留置の件数() throws Exception {
            String body = mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("通関で留置中")))
                    .andReturn().getResponse().getContentAsString();
            var matcher = java.util.regex.Pattern
                    .compile("通関で留置中.*?>(\\d+) 件<", java.util.regex.Pattern.DOTALL)
                    .matcher(body);
            assertThat(matcher.find()).as("件数のバッジが出ていること").isTrue();
            return Integer.parseInt(matcher.group(1));
        }
    }

    @Nested
    @DisplayName("認可")
    class 認可 {

        /** <strong>追跡管理者も通関申告を確認できる</strong>（{@code ui_design.md}）。 */
        @Test
        @WithMockUser(username = "tracker", roles = "TRACKER")
        void 追跡管理者は一覧と詳細を開ける() throws Exception {
            通関待ちの貨物(追跡番号(8));
            申告を登録する(追跡番号(8), "DEC-9708");

            mockMvc.perform(get("/handling/customs"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/handling/customs/{id}", 申告の識別子("DEC-9708")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("DEC-9708")));
        }

        /** <strong>営業担当者は開けない。</strong> 「一致してほしくない URL」も書く。 */
        @Test
        @WithMockUser(username = "sales", roles = "SALES")
        void 営業担当者は通関申告を開けない() throws Exception {
            mockMvc.perform(get("/handling/customs"))
                    .andExpect(status().isForbidden());
        }

        /** <strong>荷主は開けない。</strong> 通関は社内の業務である。 */
        @Test
        @WithMockUser(username = "shipper", roles = "SHIPPER")
        void 荷主は通関申告を開けない() throws Exception {
            mockMvc.perform(get("/handling/customs"))
                    .andExpect(status().isForbidden());
        }
    }
}
