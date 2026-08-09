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

        /**
         * <strong>遅延の対応中でも破損を登録できる</strong>（IT11 / C21）。
         *
         * <p>IT10 は未解決を 1 件に限っていた。破損を登録するには遅延を
         * 「解決」する必要があり、<strong>その瞬間に荷主へ事実でない対応報告が
         * 飛ぶ</strong>。実装上の都合を業務の制約にしていた。
         *
         * <p>1 件目を解決しても、まだ未解決が残るうちは例外発生のままにする。
         * ここで通常状態に戻すと、一覧から消えて誰も見なくなる。
         */
        @Test
        void 対応中の例外があっても別の例外を登録できる() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9302", "RECEIVED");
            例外を登録する(number, "DELAY");

            例外を登録する(number, "DAMAGE").andExpect(status().is3xxRedirection());

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM tracking_exception_event e
                      JOIN tracking_activity t ON t.id = e.tracking_id
                     WHERE t.tracking_number = ? AND e.resolved_at IS NULL
                    """, Integer.class, number)).isEqualTo(2);

            // 片方だけ解決しても、まだ例外の中にいる
            例外を解決する(未解決の例外の識別子(number), number, "破損は補償で対応");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT transport_status FROM tracking_activity WHERE tracking_number = ?",
                    String.class, number)).isEqualTo("EXCEPTION");
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

        /**
         * <strong>発生時点の到達性。</strong> 追跡詳細から例外の登録へ行ける。
         *
         * <p>遅延・破損・紛失に気づくのは、この画面で状況を見ているときである。
         * ここに導線が無いと、追跡管理者は URL を覚えて移動することになる。
         */
        @Test
        void 追跡詳細から例外の登録へ行ける() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9406", "RECEIVED");

            mockMvc.perform(get("/tracking/{n}", number))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("例外を登録")))
                    // **追跡番号を埋めて開く**（手で書き写させない）
                    .andExpect(content().string(Matchers.containsString(
                            "/tracking/exceptions/new?trackingNumber=" + number)));
        }

        /**
         * ダッシュボードの件数が<strong>登録に応じて増える</strong>。
         *
         * <p>「未解決の例外」という見出しが出ることだけを見ると、
         * <strong>件数を返すクエリを 0 固定に潰しても緑のまま</strong>になる。
         * それでは「開かなくても仕事の有無が分かる」という意図を何も判別しない。
         * 他のテストの例外が同時に存在しうるため、<strong>絶対値ではなく差分</strong>で見る。
         */
        @Test
        void ダッシュボードの未解決件数が登録で増える() throws Exception {
            int before = 未解決の件数();

            追跡中の貨物("TRK-20261001-9404", "RECEIVED");
            例外を登録する("TRK-20261001-9404", "DELAY");

            assertThat(未解決の件数()).isEqualTo(before + 1);
        }

        /** ダッシュボードの HTML から未解決の件数を読む。 */
        private int 未解決の件数() throws Exception {
            String body = mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("未解決の例外")))
                    .andReturn().getResponse().getContentAsString();
            var matcher = java.util.regex.Pattern
                    .compile("未解決の例外.*?>(\\d+) 件<", java.util.regex.Pattern.DOTALL)
                    .matcher(body);
            assertThat(matcher.find()).as("件数のバッジが出ていること").isTrue();
            return Integer.parseInt(matcher.group(1));
        }

        /** <strong>荷役作業員は例外を登録できない</strong>（Try T5）。 */
        @Test
        @WithMockUser(username = "handler", roles = "HANDLER")
        void 荷役作業員は例外を登録できない() throws Exception {
            mockMvc.perform(post("/tracking/exceptions")
                            .param("trackingNumber", "TRK-20261001-9405")
                            .param("exceptionType", "DELAY")
                            .param("location", 発生港())
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

        /**
         * <strong>管理者はエスカレーションの詳細を開ける。</strong>
         *
         * <p>エスカレーションは「上げたこと」ではなく「読んで判断すること」に意味がある。
         * 一覧から詳細へ行けないなら、管理者にできるのは件数を数えることだけになる。
         */
        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void 管理者はエスカレーションの詳細を開ける() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9407", "RECEIVED");
            例外を登録する(number, "LOST");
            long id = 例外の識別子(number);

            mockMvc.perform(get("/tracking/exceptions/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString(number)))
                    // **対応の記録は追跡管理者の操作である**
                    .andExpect(content().string(
                            Matchers.not(Matchers.containsString("対応を記録する"))));
        }

        /**
         * <strong>発生場所の無い例外でも追跡詳細が開ける。</strong>
         *
         * <p>{@code location_unlocode} は V22 で足した列であり、それ以前に起票された
         * 例外は場所を持ちようがない。V22 は「既存行のために NULL 可のままにする」と
         * 書いているが、<strong>読み戻す側が拒めば書いた意味が無い</strong>。
         * 落ちるのは例外 1 件ではなく集約全体であり、その貨物の画面ごと 500 になる。
         */
        @Test
        void 発生場所の無い例外を読み戻せる() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9408", "RECEIVED");
            Long trackingId = jdbcTemplate.queryForObject(
                    "SELECT id FROM tracking_activity WHERE tracking_number = ?",
                    Long.class, number);
            // V22 より前に起票された例外と同じ形（場所が無い）
            jdbcTemplate.update("""
                    INSERT INTO tracking_exception_event (
                        tracking_id, exception_type, occurred_at, escalation_flag,
                        status_before, description)
                    VALUES (?, 'DELAY', CURRENT_TIMESTAMP, FALSE, 'RECEIVED', '旧形式の例外')
                    """, trackingId);

            mockMvc.perform(get("/tracking/{n}", number))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/tracking/exceptions"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString(number)));
        }

        /**
         * <strong>新しい到着予定日の無い「解決済み」を読み戻せる</strong>（IT11 の Try T4）。
         *
         * <p>{@code revised_arrival} は V23 で足した列であり、IT10 までに解決された
         * 例外は持ちようがない。V23 は「読み戻す側は NULL を拒んではならない」と
         * 書いており、<strong>その責務をここで固定する</strong>。
         *
         * <p>IT10 では、マイグレーションのコメントと Java の実装が<strong>同じ日に、
         * 互いに矛盾したまま</strong>両方コミットされた。宣言しただけで守った気に
         * ならないよう、旧形式の行を直接 INSERT して読む。
         */
        @Test
        void 新しい到着予定日の無い解決済みの例外を読み戻せる() throws Exception {
            String number = 追跡中の貨物("TRK-20261001-9409", "RECEIVED");
            Long trackingId = jdbcTemplate.queryForObject(
                    "SELECT id FROM tracking_activity WHERE tracking_number = ?",
                    Long.class, number);
            // V23 より前に解決された例外と同じ形（新しい到着予定日が無い）
            jdbcTemplate.update("""
                    INSERT INTO tracking_exception_event (
                        tracking_id, exception_type, location_unlocode, occurred_at,
                        escalation_flag, status_before, description,
                        resolved_at, resolution_notes)
                    VALUES (?, 'DELAY', ?, CURRENT_TIMESTAMP, FALSE, 'RECEIVED',
                            '旧形式の例外', CURRENT_TIMESTAMP, '対応済み')
                    """, trackingId, 発生港());

            mockMvc.perform(get("/tracking/{n}", number))
                    .andExpect(status().isOk());
            // **一覧と詳細の両方で読める**（片方だけ通しても、もう片方で 500 になる）
            mockMvc.perform(get("/tracking/exceptions").param("resolved", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString(number)));
            mockMvc.perform(get("/tracking/exceptions/{id}", 例外の識別子(number)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("対応済み")));
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
