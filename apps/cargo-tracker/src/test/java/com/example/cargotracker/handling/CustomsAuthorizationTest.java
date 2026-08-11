package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.CargoFixture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 通関の認可（US29 / IT12 の C35）。
 *
 * <p>IT11 は {@code ui_design.md} が「登録は HANDLER / TRACKER」と定め、画面は
 * HANDLER にしかボタンを出していなかった。<strong>ボタンが見えないだけで、
 * URL を叩けば追跡管理者も登録できた</strong>（IT11 レビュー C35）。
 *
 * <p><strong>意図を決める。</strong> 申告は通関の荷役作業に紐づく現場の記録であり、
 * 出すのも状態を反映するのも<strong>荷役作業員の仕事</strong>である。
 * 追跡管理者が通関を見るのは<strong>荷主・荷受人に答えるため</strong>であって、
 * 手続きを代行するためではない。よって<strong>追跡管理者は読み取り専用</strong>とする。
 *
 * <p><strong>画面に出さないことは認可ではない。</strong> ここで確かめるのは
 * 「ボタンが無いこと」ではなく「実行できないこと」である。
 */
@AutoConfigureMockMvc
@DisplayName("通関の認可（C35）")
class CustomsAuthorizationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** **業務の暦を使う。** JVM 既定の now() だと CI（UTC）でだけずれる。 */
    @Autowired
    private java.time.Clock clock;

    private void 通関待ちの貨物(String trackingNumber) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("認可テスト商事")
                .route("KRPUS", "USSEA")
                .status("IN_TRANSIT", "ROUTED")
                .trackingNumber(trackingNumber)
                .consignee("受取花子")
                .insert();
        UUID bookingId = cargo.bookingId();
        long cargoId = cargo.cargoId();
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0088', 'KRPUS', 'USSEA',
                        TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-19 09:00:00+09', 1)
                """, cargoId);
        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'UNLOADED', 0)
                """, trackingNumber, bookingId);
    }

    private void 通関の荷役を登録する(String trackingNumber) throws Exception {
        mockMvc.perform(post("/handling")
                .param("trackingNumber", trackingNumber)
                .param("type", "CUSTOMS")
                .param("completionTime", "2026-04-20T09:00")
                .param("locationUnlocode", "USSEA")
                .param("operatorName", "港湾太郎")
                .with(user("handler").roles("HANDLER")).with(csrf()));
    }

    private int 申告の件数(String trackingNumber) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM customs_declaration d
                  JOIN handling_activity h ON h.id = d.handling_activity_id
                 WHERE h.tracking_number = ?
                """, Integer.class, trackingNumber);
    }

    /** <strong>追跡管理者は読める。</strong> 荷主・荷受人に答えるために要る。 */
    @Test
    void 追跡管理者は通関の一覧と詳細を読める() throws Exception {
        mockMvc.perform(get("/handling/customs").with(user("tracker").roles("TRACKER")))
                .andExpect(status().isOk());
    }

    /**
     * <strong>追跡管理者は申告を登録できない。</strong>
     *
     * <p>ボタンを出していないことは認可ではない。<strong>URL を叩いても
     * 実行されない</strong>ことを、入口（403）と出口（件数が増えない）で見る。
     */
    @Test
    void 追跡管理者は申告を登録できない() throws Exception {
        通関待ちの貨物("TRK-20260420-9801");
        通関の荷役を登録する("TRK-20260420-9801");

        mockMvc.perform(post("/handling/customs")
                        .param("trackingNumber", "TRK-20260420-9801")
                        .param("declarationNumber", "DEC-9801")
                        .param("declaredAt", "2026-04-20T09:30")
                        .with(user("tracker").roles("TRACKER")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(申告の件数("TRK-20260420-9801"))
                .as("追跡管理者の登録は実行されてはならない").isZero();
    }

    /**
     * <strong>追跡管理者は通関状態を変えられない。</strong>
     *
     * <p>状態は税関の答えを現場が反映するものである。追跡管理者が変えられると、
     * <strong>誰が税関から聞いたのかが記録から読めなくなる</strong>。
     */
    @Test
    void 追跡管理者は通関状態を変えられない() throws Exception {
        通関待ちの貨物("TRK-20260420-9802");
        通関の荷役を登録する("TRK-20260420-9802");
        mockMvc.perform(post("/handling/customs")
                .param("trackingNumber", "TRK-20260420-9802")
                .param("declarationNumber", "DEC-9802")
                .param("declaredAt", "2026-04-20T09:30")
                .with(user("handler").roles("HANDLER")).with(csrf()));
        long declarationId = jdbcTemplate.queryForObject("""
                SELECT d.id FROM customs_declaration d
                  JOIN handling_activity h ON h.id = d.handling_activity_id
                 WHERE h.tracking_number = ?
                """, Long.class, "TRK-20260420-9802");

        mockMvc.perform(post("/handling/customs/{id}/status", declarationId)
                        .param("status", "CLEARED")
                        .param("reason", "追跡管理者が変えた")
                        .with(user("tracker").roles("TRACKER")).with(csrf()))
                .andExpect(status().isForbidden());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM customs_declaration WHERE id = ?", String.class, declarationId);
        assertThat(status).as("状態が動いてはならない").isEqualTo("PENDING");
    }

    /**
     * <strong>まだ出していない申告を「出した」と記録できない</strong>（IT12 の C36）。
     *
     * <p>荷役の登録は同じ守りを持っており、通関だけが抜けていた。
     * ドメインが拒むだけでは<strong>利用者から見ると 500 である</strong>。
     * 拒んだこと（入口）だけでなく、<strong>申告が残らないこと</strong>（出口）まで見る。
     */
    @Test
    void 未来の申告日時は画面で拒まれ申告も残らない() throws Exception {
        通関待ちの貨物("TRK-20260420-9804");
        通関の荷役を登録する("TRK-20260420-9804");

        mockMvc.perform(post("/handling/customs")
                        .param("trackingNumber", "TRK-20260420-9804")
                        .param("declarationNumber", "DEC-9804")
                        .param("declaredAt",
                                java.time.LocalDateTime.now(clock).plusDays(1)
                                        .withNano(0).withSecond(0).toString())
                        .with(user("handler").roles("HANDLER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(申告の件数("TRK-20260420-9804"))
                .as("未来の申告は残ってはならない").isZero();
    }

    /** <strong>荷役作業員は登録できる。</strong> 全員を拒む実装で緑にしない。 */
    @Test
    void 荷役作業員は申告を登録できる() throws Exception {
        通関待ちの貨物("TRK-20260420-9803");
        通関の荷役を登録する("TRK-20260420-9803");

        mockMvc.perform(post("/handling/customs")
                .param("trackingNumber", "TRK-20260420-9803")
                .param("declarationNumber", "DEC-9803")
                .param("declaredAt", "2026-04-20T09:30")
                .with(user("handler").roles("HANDLER")).with(csrf()));

        assertThat(申告の件数("TRK-20260420-9803")).isEqualTo(1);
    }
}
