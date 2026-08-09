package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 例外の安全装置と到達性（US19 / US20）。
 *
 * <p><strong>集約の単体テストは画面での見え方を判別しない</strong>（IT9 の Try T1）。
 * 「引取済みには登録できない」は集約が守っているが、画面から踏んだときに 500 で
 * 現れるなら利用者には障害にしか見えない。だから<strong>ドメインが例外で拒む経路には、
 * 画面から踏むテストを対にする</strong>。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "tracker", roles = "TRACKER")
@DisplayName("例外の安全装置と到達性")
class ExceptionSafetyTest extends ExceptionTestBase {

    @Nested
    @DisplayName("画面から踏む安全装置（Try T1）")
    class 安全装置 {

        /**
         * <strong>引取が完了した貨物には登録できない。</strong>
         *
         * <p>集約が {@code IllegalStateException} で拒むが、それが画面で 500 に
         * なるなら利用者には障害にしか見えない。<strong>拒否の理由が読める形で
         * 返ることまで確かめる</strong>（IT9 の Try T1）。
         */
        @Test
        void 引取が完了した貨物には登録できない() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9301", "CLAIMED");

            例外を登録する(number, "DELAY")
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("flashError",
                                    Matchers.containsString("引取が完了した貨物")));

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM tracking_exception_event e
                      JOIN tracking_activity t ON t.id = e.tracking_id
                     WHERE t.tracking_number = ?
                    """, Integer.class, number)).isZero();
        }

        /** <strong>未解決の例外があるときは登録できない。</strong> */
        @Test
        void 未解決の例外があるときは登録できない() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9302", "RECEIVED");
            例外を登録する(number, "DELAY");

            例外を登録する(number, "DAMAGE")
                    .andExpect(flash().attribute("flashError",
                                    Matchers.containsString("未解決の例外")));
        }

        /** <strong>二度は解決できない。</strong> 最初の対応日時が上書きされてしまう。 */
        @Test
        void 解決済みの例外は再解決できない() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9303", "RECEIVED");
            例外を登録する(number, "DELAY");
            long id = 例外の識別子(number);
            例外を解決する(id, number, "最初の対応");
            Object first = 例外の行(number).get("resolved_at");

            mockMvc.perform(post("/tracking/exceptions/{id}/resolve", id)
                            .param("trackingNumber", number)
                            .param("resolutionNotes", "やり直し").with(csrf()))
                    .andExpect(flash().attribute("flashError",
                                    Matchers.containsString("すでに")));

            assertThat(例外の行(number).get("resolved_at")).isEqualTo(first);
            assertThat(例外の行(number).get("resolution_notes").toString())
                    .contains("最初の対応");
        }

        /** <strong>空の対応報告を荷主に送らない。</strong> */
        @Test
        void 対応内容が空なら解決できない() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9304", "RECEIVED");
            例外を登録する(number, "DELAY");
            long id = 例外の識別子(number);

            mockMvc.perform(post("/tracking/exceptions/{id}/resolve", id)
                            .param("trackingNumber", number)
                            .param("resolutionNotes", " ").with(csrf()))
                    .andExpect(flash().attribute("flashError",
                                    Matchers.containsString("対応内容は必須")));

            assertThat(例外の行(number).get("resolved_at")).isNull();
        }

        /** <strong>マスタに無い港は受け付けない。</strong> */
        @Test
        void 登録されていない港では登録できない() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9305", "RECEIVED");

            mockMvc.perform(post("/tracking/exceptions")
                            .param("trackingNumber", number)
                            .param("exceptionType", "DELAY")
                            .param("location", "ZZZZZ")
                            .param("occurredAt", 発生日時())
                            .with(csrf()))
                    .andExpect(flash().attribute("flashError",
                                    Matchers.containsString("登録されていない港")));
        }

        /**
         * <strong>画面から選べない種別は送っても通らない</strong>（Try T5）。
         *
         * <p>選択肢から消すだけでは、リクエストを直接組み立てれば送れてしまう。
         */
        @Test
        void 税関保留は画面から登録できない() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9306", "RECEIVED");

            例外を登録する(number, "CUSTOMS_HOLD")
                    .andExpect(flash().attribute("flashError",
                                    Matchers.containsString("画面から登録できない")));
        }
    }

    @Nested
    @DisplayName("到達性と認可")
    class 到達性 {

        /** <strong>一覧は未解決を先に出す</strong>（連絡すべき仕事の待ち行列）。 */
        @Test
        void 一覧に未解決の例外と荷主名が並ぶ() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9401", "RECEIVED");
            例外を登録する(number, "DELAY");

            mockMvc.perform(get("/tracking/exceptions"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString(number)))
                    // **誰に連絡するのかが読める**（IT9 の Try T2）
                    .andExpect(content().string(Matchers.containsString("例外テスト商事")));
        }

        /** 解決済みは既定では出さない。**片づいたものが混ざると待ち行列が読めない。** */
        @Test
        void 解決済みは既定では一覧に出ない() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9402", "RECEIVED");
            例外を登録する(number, "DELAY");
            long id = 例外の識別子(number);
            例外を解決する(id, number, "対応済み");

            mockMvc.perform(get("/tracking/exceptions"))
                    .andExpect(content().string(Matchers.not(Matchers.containsString(number))));
            mockMvc.perform(get("/tracking/exceptions").param("resolved", "true"))
                    .andExpect(content().string(Matchers.containsString(number)));
        }

        /** <strong>状態軸の到達性。</strong> 例外の詳細から解決へ進める。 */
        @Test
        void 例外の詳細に対応フォームが出る() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9403", "UNLOADED");
            例外を登録する(number, "DELAY");
            long id = 例外の識別子(number);

            mockMvc.perform(get("/tracking/exceptions/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("対応内容を記録する")))
                    // **押す前に、解決したらどこへ戻るのかが読める**
                    .andExpect(content().string(Matchers.containsString("荷降ろし済")));
        }

        /** ダッシュボードに未解決の件数が出る（**開かなくても仕事の有無が分かる**）。 */
        @Test
        void ダッシュボードに未解決の件数が出る() throws Exception {
            追跡中の貨物("TRK-20261001-9404", "RECEIVED");
            例外を登録する("TRK-20261001-9404", "DELAY");

            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("未解決の例外")));
        }

        /** <strong>荷役作業員は例外を登録できない</strong>（Try T5）。 */
        @Test
        @WithMockUser(username = "handler", roles = "HANDLER")
        void 荷役作業員は例外を登録できない() throws Exception {
            mockMvc.perform(post("/tracking/exceptions")
                            .param("trackingNumber", "TRK-20261001-9405")
                            .param("exceptionType", "DELAY")
                            .param("location", 発生港)
                            .param("occurredAt", 発生日時())
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }

        /**
         * <strong>追跡管理者はエスカレーションの一覧を開けない。</strong>
         *
         * <p>「一致してほしくない URL」もテストに書く（Try T5）。管理者向けの規則を
         * 追跡管理者の規則より後ろに書くと、この検査だけが落ちる。
         */
        @Test
        void 追跡管理者はエスカレーションの一覧を開けない() throws Exception {
            mockMvc.perform(get("/tracking/exceptions/escalated"))
                    .andExpect(status().isForbidden());
        }

        /** <strong>管理者は例外の一覧そのものは開けない。</strong> */
        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void 管理者は例外の一覧そのものは開けない() throws Exception {
            mockMvc.perform(get("/tracking/exceptions"))
                    .andExpect(status().isForbidden());
        }
    }
}
