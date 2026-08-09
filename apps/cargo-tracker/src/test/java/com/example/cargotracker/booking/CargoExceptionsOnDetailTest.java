package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 予約詳細で「この貨物の例外」を読む（US19 / IT12 の C31）。
 *
 * <p><strong>営業担当者が誤配や遅延の記録を追えない。</strong> 例外は追跡管理者の
 * 画面にだけあり、荷主から「どうなっているのか」と問われる当人が読めない
 * （IT11 レビュー C31）。予約詳細は営業担当者が荷主対応のときに開く画面である。
 *
 * <p><strong>読み取り専用である。</strong> 解決の登録は追跡管理者の仕事であり、
 * ここでは動かさない。<strong>読めることと操作できることを混ぜない。</strong>
 *
 * <p>ここで確かめるのは「出ること」だけではない。例外が無い貨物に節を出さないこと、
 * <strong>対応済と未解決を見分けられること</strong>まで見る。
 * 全部を「未解決」と出す実装や、常に節を出す実装で緑にしない。
 */
@AutoConfigureMockMvc
@DisplayName("予約詳細の例外表示（C31）")
class CargoExceptionsOnDetailTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 追跡中の貨物を用意する。 */
    private UUID 追跡中の貨物(String trackingNumber) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '例外表示テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq),
                "exception-view-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number)
                VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'USLAX', CURRENT_DATE + 60,
                        'IN_TRANSIT', 'ROUTED', ?)
                """, bookingId, shipperId, trackingNumber);

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
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version,
                    destination_unlocode, estimated_arrival_date)
                VALUES (?, ?, 'EXCEPTION', 0, 'USLAX', DATE '2026-04-20')
                """, trackingNumber, bookingId);
        return bookingId;
    }

    private void 例外を起こす(
            String trackingNumber, String type, String description, boolean resolved) {
        Long trackingId = jdbcTemplate.queryForObject(
                "SELECT id FROM tracking_activity WHERE tracking_number = ?",
                Long.class, trackingNumber);
        jdbcTemplate.update("""
                INSERT INTO tracking_exception_event (
                    tracking_id, exception_type, occurred_at, escalation_flag,
                    status_before, description, location_unlocode,
                    resolved_at, resolution_notes)
                VALUES (?, ?, TIMESTAMP WITH TIME ZONE '2026-04-10 09:00:00+09', FALSE,
                        'ONBOARD_CARRIER', ?, 'JPOSA', ?, ?)
                """, trackingId, type, description,
                resolved ? java.sql.Timestamp.valueOf("2026-04-11 09:00:00") : null,
                resolved ? "代替便を手配した" : null);
    }

    private String 予約詳細(UUID bookingId) throws Exception {
        return mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 受入基準: 営業担当者が予約詳細で例外の記録を読める。 */
    @Test
    void 営業担当者が例外の記録を読める() throws Exception {
        UUID bookingId = 追跡中の貨物("TRK-20260410-9701");
        例外を起こす("TRK-20260410-9701", "DELAY", "台風により沖待ちが発生", false);

        assertThat(予約詳細(bookingId))
                .contains("この貨物の例外")
                .contains("遅延")
                .contains("台風により沖待ちが発生")
                .contains("2026-04-10");
    }

    /**
     * <strong>対応済と未解決を見分けられる。</strong>
     *
     * <p>荷主に説明するとき、片づいた話と現在進行中の話は別である。
     * すべてを同じ見た目で出すと、営業担当者は 1 件ずつ追跡側へ確かめに行く。
     */
    @Test
    void 対応済と未解決を見分けられる() throws Exception {
        UUID bookingId = 追跡中の貨物("TRK-20260410-9702");
        例外を起こす("TRK-20260410-9702", "DELAY", "台風により沖待ち", true);
        例外を起こす("TRK-20260410-9702", "MISROUTED", "誤った港で荷降し", false);

        String html = 予約詳細(bookingId);
        assertThat(html).contains("対応済").contains("代替便を手配した");
        assertThat(html).contains("未解決");
    }

    /**
     * <strong>例外が無い貨物には節を出さない。</strong>
     *
     * <p>これが無いと、常に節を出す実装でも上の 2 件が緑になる。
     * 何も起きていない貨物に「例外」の見出しが出続けると、
     * <strong>見出しそのものが読み飛ばされる</strong>。
     */
    @Test
    void 例外が無い貨物には節を出さない() throws Exception {
        UUID bookingId = 追跡中の貨物("TRK-20260410-9703");

        assertThat(予約詳細(bookingId)).doesNotContain("この貨物の例外");
    }

    /**
     * <strong>追跡番号が未発行の予約でも詳細が開ける</strong>（T1 の数え上げ）。
     *
     * <p>ACL のアダプタが「形式の違う追跡番号を例外にしない」と書いている。
     * 例外にすると、<strong>予約詳細を開いただけで 500 になる</strong>
     * （追跡番号は確定後に発行されるため、未発行の予約は日常的にある）。
     */
    @Test
    void 追跡番号が未発行の予約でも詳細が開ける() throws Exception {
        UUID bookingId = 追跡中の貨物("TRK-20260410-9705");
        jdbcTemplate.update(
                "UPDATE cargo SET tracking_number = NULL, booking_status = 'CONFIRMED' "
                        + "WHERE booking_id = ?", bookingId);

        assertThat(予約詳細(bookingId)).contains("予約詳細");
    }

    /**
     * <strong>読み取り専用である。</strong>
     *
     * <p>解決の登録は追跡管理者の仕事である（{@code /tracking/exceptions/{id}/resolve}）。
     * 予約詳細に操作を置くと、権限の分かれ目が画面ごとにばらける。
     */
    @Test
    void 予約詳細から例外を操作できない() throws Exception {
        UUID bookingId = 追跡中の貨物("TRK-20260410-9704");
        例外を起こす("TRK-20260410-9704", "DELAY", "台風により沖待ち", false);

        assertThat(予約詳細(bookingId)).doesNotContain("/resolve");
    }
}
