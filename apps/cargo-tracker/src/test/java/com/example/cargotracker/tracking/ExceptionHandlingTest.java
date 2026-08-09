package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * US19 / US20: 遅延・破損・紛失の例外を処理する。受入基準に 1:1 で対応させる。
 *
 * <p>安全装置と到達性は {@link ExceptionSafetyTest} が受け持つ。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "tracker", roles = "TRACKER")
@DisplayName("US19 / US20 例外を処理する")
class ExceptionHandlingTest extends ExceptionTestBase {

    @Nested
    @DisplayName("US19 遅延例外を処理する")
    class 遅延 {

        /** 受入基準: 追跡番号と種別「遅延」・発生状況（場所・日時・理由）を記録できる。 */
        @Test
        void 遅延を発生状況とともに記録できる() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9101", "RECEIVED");

            例外を登録する(number, "DELAY").andExpect(status().is3xxRedirection());

            var row = 例外の行(number);
            assertThat(row.get("exception_type")).isEqualTo("DELAY");
            assertThat(row.get("location_unlocode")).isEqualTo(発生港);
            assertThat(row.get("occurred_at")).isNotNull();
            assertThat(row.get("description").toString()).contains("足止め");
        }

        /** 受入基準: 記録後、貨物状態が「例外発生」に更新される。 */
        @Test
        void 記録すると貨物状態が例外発生になる() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9102", "RECEIVED");

            例外を登録する(number, "DELAY");

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT transport_status FROM tracking_activity WHERE tracking_number = ?",
                    String.class, number)).isEqualTo("EXCEPTION");
        }

        /**
         * 受入基準: <strong>荷主に遅延発生の通知が送信される。</strong>
         *
         * <p>ADR-006 により外部へは送らない。<strong>通知の実体は記録である</strong>
         * （US12 で確立）。予約詳細の通知履歴に現れることをもって「知らせた」とする。
         */
        @Test
        void 荷主への発生通知が記録される() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9103", "RECEIVED");

            例外を登録する(number, "DELAY");

            var types = jdbcTemplate.queryForList("""
                    SELECT n.notification_type FROM booking_notification n
                      JOIN cargo c ON c.booking_id = n.booking_id
                     WHERE c.tracking_number = ?
                    """, String.class, number);
            assertThat(types).contains("EXCEPTION_RAISED");
        }

        /** 受入基準: 対応内容を入力して荷主に対応報告を送信できる。 */
        @Test
        void 対応内容を入力すると対応報告が記録される() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9104", "RECEIVED");
            例外を登録する(number, "DELAY");
            long id = 例外の識別子(number);

            例外を解決する(id, number, "代替便に振り替えました。到着は 9 月 22 日です")
                    .andExpect(status().is3xxRedirection());

            var types = jdbcTemplate.queryForList("""
                    SELECT n.notification_type FROM booking_notification n
                      JOIN cargo c ON c.booking_id = n.booking_id
                     WHERE c.tracking_number = ?
                    """, String.class, number);
            // **発生と対応報告は別の種別で積む。** 同じにすると履歴で区別できない
            assertThat(types).contains("EXCEPTION_RAISED", "EXCEPTION_RESOLVED");
        }

        /**
         * <strong>解決すると発生前の状態に戻る</strong>（本 IT の最重要）。
         *
         * <p>発生前の状態を永続化しているからこそ、リクエストをまたいでも
         * 正しく戻せる。履歴から導き直すと、ここが「未受取」や別の状態になる。
         */
        @Test
        void 解決すると発生前の状態に戻る() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9105", "UNLOADED");
            例外を登録する(number, "DELAY");
            long id = 例外の識別子(number);

            例外を解決する(id, number, "対応しました");

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT transport_status FROM tracking_activity WHERE tracking_number = ?",
                    String.class, number)).isEqualTo("UNLOADED");
        }

        /** 受入基準: 例外対応履歴が記録される（解決日時と対応内容が残る）。 */
        @Test
        void 対応履歴が残る() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9106", "RECEIVED");
            例外を登録する(number, "DELAY");
            long id = 例外の識別子(number);

            例外を解決する(id, number, "代替便に振り替えました");

            var row = 例外の行(number);
            assertThat(row.get("resolved_at")).isNotNull();
            assertThat(row.get("resolution_notes").toString()).contains("代替便");
        }
    }

    @Nested
    @DisplayName("US20 破損・紛失例外を処理する")
    class 破損と紛失 {

        /** 受入基準: 種別「破損」または「紛失」・発生状況を記録できる。 */
        @Test
        void 破損を記録できる() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9201", "RECEIVED");

            例外を登録する(number, "DAMAGE").andExpect(status().is3xxRedirection());

            assertThat(例外の行(number).get("exception_type")).isEqualTo("DAMAGE");
        }

        /** 受入基準: <strong>紛失は緊急フラグが設定される。</strong> */
        @Test
        void 紛失は緊急フラグが立つ() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9202", "RECEIVED");

            例外を登録する(number, "LOST");

            assertThat(例外の行(number).get("escalation_flag")).isEqualTo(Boolean.TRUE);
        }

        /**
         * <strong>破損では立たない。</strong>
         *
         * <p>「立つこと」だけを確かめると、常に true を返す実装でも緑になる。
         */
        @Test
        void 破損では緊急フラグは立たない() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9203", "RECEIVED");

            例外を登録する(number, "DAMAGE");

            assertThat(例外の行(number).get("escalation_flag")).isEqualTo(Boolean.FALSE);
        }

        /**
         * 受入基準: <strong>管理職への escalation 通知が送信される。</strong>
         *
         * <p>ADR-006 により外部へは送らない。<strong>管理者が見る場所を作ることで
         * 満たす</strong> — 記録しただけで誰も見ないなら、エスカレーションに意味は無い
         * （IT9 の Try T2）。
         */
        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void 管理者がエスカレーション中の例外を見られる() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9204", "RECEIVED");
            例外を登録する(number, "LOST");

            mockMvc.perform(get("/tracking/exceptions/escalated"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString(number)))
                    .andExpect(content().string(Matchers.containsString("例外テスト商事")));
        }

        /** <strong>破損はエスカレーションの一覧に現れない。</strong> */
        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void 破損はエスカレーションの一覧に現れない() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9205", "RECEIVED");
            例外を登録する(number, "DAMAGE");

            mockMvc.perform(get("/tracking/exceptions/escalated"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.not(Matchers.containsString(number))));
        }

        /** 受入基準: 対応内容（補償方針等）を入力して荷主に報告を送信できる。 */
        @Test
        void 紛失の対応報告を記録できる() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9206", "RECEIVED");
            例外を登録する(number, "LOST");
            long id = 例外の識別子(number);

            例外を解決する(id, number, "保険手続きを開始しました。全額を補償します")
                    .andExpect(status().is3xxRedirection());

            assertThat(例外の行(number).get("resolution_notes").toString()).contains("補償");
        }

    }
}
