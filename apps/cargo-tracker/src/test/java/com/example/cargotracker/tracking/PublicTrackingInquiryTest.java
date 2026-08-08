package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 公開追跡（US18。認証不要）。
 *
 * <p><strong>本システムで認証を持たない相手に見せる唯一の画面である。</strong>
 * 荷主が取引先へ URL を転送するのは日常的に起きるため、
 * <strong>見せてよい情報の範囲がそのまま設計上の制約になる</strong>。
 *
 * <p>ここで壊すのは「出口」である。個人情報を渡していないこと（入口）だけでなく、
 * <strong>応答の本文に現れないこと</strong>まで確かめる。項目を足した瞬間に
 * 落ちる形にしておかないと、次に画面を触る人が気づかずに足す。
 */
@AutoConfigureMockMvc
@DisplayName("公開追跡（US18）")
class PublicTrackingInquiryTest extends PostgreSQLIntegrationTestBase {

    private static final String 荷主名 = "秘密物産株式会社";
    private static final String 荷主メール = "himitsu@example.com";
    private static final String 荷主住所 = "梅田 9-9-9";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 追跡番号を発行し、受領と積込まで進めた貨物を用意する。 */
    private String 追跡中の貨物(String trackingNumber) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', ?, ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', ?)
                """,
                shipperId, "SHP-%06d".formatted(seq), 荷主名,
                "public-%d-%s".formatted(seq, 荷主メール), 荷主住所);

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number)
                VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'USLAX', CURRENT_DATE + 60,
                        'IN_TRANSIT', 'ROUTED', ?)
                """, bookingId, shipperId, trackingNumber);

        // **経路を割り当てた貨物には旅程が要る**（CargoRouting の不変条件）。
        // 状態だけ ROUTED にして区間を入れないと、集約の復元で弾かれる
        Long cargoId = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0001', 'JPOSA', 'USLAX',
                        TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-20 09:00:00+09', 1)
                """, cargoId);

        jdbcTemplate.update("""
                INSERT INTO tracking_activity (tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'LOADED', 0)
                """, trackingNumber, bookingId);
        Long trackingId = jdbcTemplate.queryForObject(
                "SELECT id FROM tracking_activity WHERE tracking_number = ?",
                Long.class, trackingNumber);
        jdbcTemplate.update("""
                INSERT INTO tracking_handling_event (
                    tracking_id, event_type, event_time, location_unlocode, voyage_number)
                VALUES (?, 'RECEIVE', TIMESTAMP WITH TIME ZONE '2026-04-01 09:00:00+09',
                        'JPOSA', NULL)
                """, trackingId);
        jdbcTemplate.update("""
                INSERT INTO tracking_handling_event (
                    tracking_id, event_type, event_time, location_unlocode, voyage_number)
                VALUES (?, 'LOAD', TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        'JPOSA', 'V0001')
                """, trackingId);
        return bookingId.toString();
    }

    /** 受入基準: ログインなしでも追跡番号があれば照会できる。 */
    @Test
    void 未ログインでも追跡番号があれば照会できる() throws Exception {
        追跡中の貨物("TRK-20260401-8001");

        mockMvc.perform(get("/public/tracking").param("trackingNumber", "TRK-20260401-8001"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("TRK-20260401-8001")))
                // 受入基準: 現在の状態が表示される
                .andExpect(content().string(Matchers.containsString("積み込み済")));
    }

    /** 受入基準: 追跡イベント履歴（日時・場所・作業種別）が時系列で表示される。 */
    @Test
    void イベント履歴が表示される() throws Exception {
        追跡中の貨物("TRK-20260401-8002");

        mockMvc.perform(get("/public/tracking/{n}", "TRK-20260401-8002"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("受領")))
                .andExpect(content().string(Matchers.containsString("積込")))
                .andExpect(content().string(Matchers.containsString("JPOSA")));
    }

    /**
     * <strong>個人情報を返さない。</strong> 荷主が取引先へ URL を転送するのは
     * 日常的に起きる。ここに荷主の名前・住所・連絡先が出ると、
     * <strong>取引関係そのものが漏れる</strong>。
     *
     * <p>これは「入れないようにした」ではなく「入っていない」ことの検査である。
     * 画面に項目を足した瞬間に落ちる。
     */
    @Test
    void 個人情報は本文に現れない() throws Exception {
        追跡中の貨物("TRK-20260401-8003");

        String body = mockMvc.perform(
                        get("/public/tracking/{n}", "TRK-20260401-8003"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain(荷主名)
                .doesNotContain(荷主住所)
                .doesNotContain("himitsu@example.com")
                .doesNotContain("06-1234-5678");
    }

    /**
     * <strong>存在しない番号と形式の違う番号を区別しない</strong>（列挙攻撃対策）。
     *
     * <p>区別すると「形式は正しいが存在しない」という答えが返り、
     * <strong>番号の総当たりで貨物の有無を確かめられる</strong>。
     * 追跡番号は日付＋連番であり、推測できる形をしている。
     */
    @Test
    void 存在しない番号と形式違いが同じことばで返る() throws Exception {
        追跡中の貨物("TRK-20260401-8004");

        List<String> notFoundInputs = List.of(
                "TRK-19990101-0001",  // 形式は正しいが存在しない
                "ABC",                 // 形式が違う
                "TRK-2026-1",          // 桁が違う
                "'; DROP TABLE cargo;--");

        for (String input : notFoundInputs) {
            mockMvc.perform(get("/public/tracking").param("trackingNumber", input))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            Matchers.containsString("該当する貨物が見つかりません")));
        }
    }

    /** 番号を渡さずに開くと入力フォームが出る（エラーにしない）。 */
    @Test
    void 番号なしで開くと入力フォームが出る() throws Exception {
        mockMvc.perform(get("/public/tracking"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("追跡する")))
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("見つかりません"))));
    }

    /**
     * <strong>大小文字を問わない。</strong> QR から読み取れず手で打ち直す場面は多い。
     */
    @Test
    void 小文字で入力しても照会できる() throws Exception {
        追跡中の貨物("TRK-20260401-8005");

        mockMvc.perform(get("/public/tracking").param("trackingNumber", "trk-20260401-8005"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("TRK-20260401-8005")));
    }
}
