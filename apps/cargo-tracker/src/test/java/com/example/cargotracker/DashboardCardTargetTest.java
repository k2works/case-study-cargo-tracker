package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * ダッシュボードのカードが指す先（ADR-014 / IT12 の C33 / C34）。
 *
 * <p><strong>件数と行き先が食い違うと、数えた仕事にたどり着けない。</strong>
 * IT11 は「留置のまま 3 日を超えた申告」の件数を出しながら、リンク先は
 * 留置の全件だった（C33）。カードが 2 件と言い、開くと 20 件並ぶ。
 *
 * <p>ADR-014 の表が挙げる<strong>誤配のカードは実装されていなかった</strong>（C34）。
 * 誤配は貨物が予定と違う港にある状態であり、<strong>気づくのが遅れるほど
 * 積み替えの選択肢が減る</strong>。
 *
 * <p>ここで確かめるのは「カードがあること」ではなく、
 * <strong>数えた対象にそのまま行けること</strong>である
 * （IT9 のふりかえり T2）。
 */
@AutoConfigureMockMvc
@DisplayName("ダッシュボードのカードの行き先（C33 / C34）")
class DashboardCardTargetTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID 誤配の貨物(String trackingNumber) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("誤配カードテスト商事")
                .status("IN_TRANSIT", "MISROUTED")
                .trackingNumber(trackingNumber)
                .insert();
        return cargo.bookingId();
    }

    private UUID 順調な貨物(String trackingNumber) {
        UUID bookingId = 誤配の貨物(trackingNumber);
        jdbcTemplate.update(
                "UPDATE cargo SET routing_status = 'ROUTED' WHERE booking_id = ?", bookingId);
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
        return bookingId;
    }

    private String ダッシュボード(String username, String role) throws Exception {
        return mockMvc.perform(get("/").with(user(username).roles(role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * <strong>「3 日を超えた申告」を数えたなら、その一覧へ行く</strong>（C33）。
     *
     * <p>留置の全件へ行くと、カードが 2 件と言い、開くと 20 件並ぶ。
     * <strong>どれが放置されているのかは、開いた先で数え直すことになる。</strong>
     */
    @Test
    void 留置のカードは3日を超えた申告の一覧へ行く() throws Exception {
        assertThat(ダッシュボード("tracker", "TRACKER"))
                .contains("days=3");
    }

    /** <strong>誤配のカードがある</strong>（C34。ADR-014 の表が挙げている）。 */
    @Test
    void 誤配のカードが追跡管理者に出る() throws Exception {
        誤配の貨物("TRK-20260420-9951");

        assertThat(ダッシュボード("tracker", "TRACKER"))
                .contains("誤配")
                .contains("routing=MISROUTED");
    }

    /**
     * <strong>件数は誤配だけを数える。</strong>
     *
     * <p>全件を数える実装でも上の 1 件は緑になる。その実装だと、
     * <strong>誤配が 0 件でもカードが赤く光り続ける</strong>。
     */
    @Test
    void 誤配の件数は誤配の貨物だけを数える() throws Exception {
        誤配の貨物("TRK-20260420-9952");
        順調な貨物("TRK-20260420-9953");

        int expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cargo WHERE routing_status = 'MISROUTED'", Integer.class);

        assertThat(ダッシュボード("tracker", "TRACKER"))
                .contains("%d 件".formatted(expected));
    }

    /**
     * <strong>誤配の一覧が実際に絞り込まれる。</strong>
     *
     * <p>リンク先を書いただけでは行き先にならない。
     * <strong>絞り込みを受け取らない一覧に飛ばすと、全件が並ぶ。</strong>
     */
    @Test
    void 誤配の一覧は誤配だけを並べる() throws Exception {
        誤配の貨物("TRK-20260420-9954");
        順調な貨物("TRK-20260420-9955");

        String html = mockMvc.perform(get("/bookings").param("routing", "MISROUTED")
                        .with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("TRK-20260420-9954");
        assertThat(html).doesNotContain("TRK-20260420-9955");
    }
}
