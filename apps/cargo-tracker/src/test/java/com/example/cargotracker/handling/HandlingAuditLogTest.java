package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.cargotracker.support.LogCapture;
import com.example.cargotracker.support.CargoFixture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 荷役の監査ログ（IT12 の C43）。
 *
 * <p><strong>「出している」と「確かめている」は別である。</strong> 監査ログは
 * {@code audit.*} の 7 つのロガーに出しているが、テストがあるのは
 * {@code audit.booking} と {@code audit.shipper} だけだった（IT11 レビュー C43）。
 * ログの書式を変えたり行を消したりしても、どの検査も落ちない。
 *
 * <p><strong>拒んだ事実こそ残す。</strong> 成功だけを記録すると、
 * <strong>確認コードを変えながら何度も試した形跡が残らない</strong>。
 * US35（引取確認コードの照合）が「一致しなかった事実が監査ログに残る」と
 * 求めているのはこれである。
 *
 * <p>個人情報は<strong>ログにも出さない</strong>。荷受人氏名や確認コードそのものを
 * 書くと、ログの閲覧権限が実質的に個人情報の閲覧権限になる。
 */
@AutoConfigureMockMvc
@DisplayName("荷役の監査ログ（C43）")
class HandlingAuditLogTest extends PostgreSQLIntegrationTestBase {

    private static final String AUDIT_LOGGER = "audit.handling";
    private static final String CONSIGNEE = "受取花子";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void 荷降し済みの貨物(String trackingNumber) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("監査ログテスト商事")
                .status("IN_TRANSIT", "ROUTED")
                .trackingNumber(trackingNumber)
                .consignee(CONSIGNEE)
                .insert();
        UUID bookingId = cargo.bookingId();
        long cargoId = cargo.cargoId();
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0077', 'JPOSA', 'USLAX',
                        TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-19 09:00:00+09', 1)
                """, cargoId);
        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'UNLOADED', 0)
                """, trackingNumber, bookingId);
    }

    /** 荷役の登録は監査ログに残る。**誰が・いつ・どこで・何をしたか。** */
    @Test
    void 荷役の登録が監査ログに残る() throws Exception {
        荷降し済みの貨物("TRK-20260420-9901");

        try (LogCapture audit = LogCapture.of(AUDIT_LOGGER)) {
            mockMvc.perform(post("/handling")
                    .param("trackingNumber", "TRK-20260420-9901")
                    .param("type", "CUSTOMS")
                    .param("completionTime", "2026-04-20T09:00")
                    .param("locationUnlocode", "USLAX")
                    .param("operatorName", "港湾太郎")
                    .with(user("handler").roles("HANDLER")).with(csrf()));

            assertThat(audit.messages())
                    .anySatisfy(message -> assertThat(message)
                            .contains("荷役登録")
                            .contains("TRK-20260420-9901")
                            .contains("CUSTOMS")
                            .contains("港湾太郎"));
        }
    }

    /**
     * <strong>拒んだ事実も監査ログに残る。</strong>
     *
     * <p>成功だけを記録すると、<strong>何度も試した形跡が残らない</strong>。
     * 総当たりは「1 回の成功」ではなく「多数の失敗」として現れる。
     *
     * <p>ここでは通関前の引取（US29 の唯一の「止まる仕組み」）を踏む。
     */
    @Test
    void 拒んだ引取も監査ログに残る() throws Exception {
        荷降し済みの貨物("TRK-20260420-9902");

        try (LogCapture audit = LogCapture.of(AUDIT_LOGGER)) {
            mockMvc.perform(post("/handling")
                    .param("trackingNumber", "TRK-20260420-9902")
                    .param("type", "CLAIM")
                    .param("completionTime", "2026-04-21T10:00")
                    .param("locationUnlocode", "USLAX")
                    .param("confirmationCode", "999999")
                    .param("consigneeName", "別人一郎")
                    .param("operatorName", "港湾太郎")
                    .with(user("handler").roles("HANDLER")).with(csrf()));

            assertThat(audit.messages())
                    .as("拒んだ事実が残らないと、総当たりの形跡が読めない")
                    .anySatisfy(message -> assertThat(message)
                            .contains("拒否")
                            .contains("TRK-20260420-9902"));
        }
    }

    /**
     * <strong>個人情報と確認コードはログに出さない。</strong>
     *
     * <p>ログの閲覧権限が実質的に個人情報の閲覧権限になる。
     * <strong>拒んだ事実を残すことと、何を入力したかを残すことは別である。</strong>
     */
    @Test
    void 荷受人氏名と確認コードはログに出ない() throws Exception {
        荷降し済みの貨物("TRK-20260420-9903");

        try (LogCapture audit = LogCapture.of(AUDIT_LOGGER)) {
            mockMvc.perform(post("/handling")
                    .param("trackingNumber", "TRK-20260420-9903")
                    .param("type", "CLAIM")
                    .param("completionTime", "2026-04-21T10:00")
                    .param("locationUnlocode", "USLAX")
                    .param("confirmationCode", "424242")
                    .param("consigneeName", CONSIGNEE)
                    .param("operatorName", "港湾太郎")
                    .with(user("handler").roles("HANDLER")).with(csrf()));

            assertThat(audit.messages())
                    .as("監査ログは出ていること（何も出ていないと下の検査が空振りする）")
                    .isNotEmpty();
            assertThat(String.join("\n", audit.messages()))
                    .doesNotContain("424242")
                    .doesNotContain(CONSIGNEE);
        }
    }
}
