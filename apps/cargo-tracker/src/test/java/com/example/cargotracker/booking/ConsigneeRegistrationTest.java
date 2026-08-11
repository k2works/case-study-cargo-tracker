package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.CargoFixture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 予約への荷受人の登録（US16 の受入基準「予約に荷受人を登録でき、引取時の
 * 本人確認に用いる」）。
 *
 * <p><strong>US04（予約登録）ではなく本ストーリーで扱う。</strong> 旧版の予約登録画面は
 * 荷受人 3 項目を必須としていたが、{@code user_story.md} の US04 の受入基準に無く、
 * {@code cargo} テーブルにも住所の列が無かった。<strong>荷受人の情報を最初に実際に使うのが
 * 引取時の本人確認である</strong>ため、担当を本ストーリーに定めている。
 */
@AutoConfigureMockMvc
@DisplayName("予約への荷受人の登録（US16）")
class ConsigneeRegistrationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID 予約(String bookingStatus) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("山田物産株式会社")
                .status(bookingStatus, "NOT_ROUTED")
                .insert();
        return cargo.bookingId();
    }

    private org.springframework.test.web.servlet.ResultActions 荷受人を登録する(
            UUID bookingId, String name, String address, String email) throws Exception {
        return mockMvc.perform(post("/bookings/{id}/consignee", bookingId)
                .param("consigneeName", name)
                .param("consigneeAddress", address == null ? "" : address)
                .param("consigneeEmail", email == null ? "" : email)
                .with(user("sales").roles("SALES")).with(csrf()));
    }

    /** 受入基準: 予約に荷受人（氏名・住所・連絡先メール）を登録できる。 */
    @Test
    void 荷受人を登録できる() throws Exception {
        UUID bookingId = 予約("PRELIMINARY");

        荷受人を登録する(bookingId, "受取花子", "Los Angeles, CA", "hanako@example.com")
                .andExpect(redirectedUrl("/bookings/" + bookingId));

        var row = jdbcTemplate.queryForMap("""
                SELECT consignee_name, consignee_address, consignee_email
                  FROM cargo WHERE booking_id = ?
                """, bookingId);
        assertThat(row.get("consignee_name")).isEqualTo("受取花子");
        assertThat(row.get("consignee_address")).isEqualTo("Los Angeles, CA");
        assertThat(row.get("consignee_email")).isEqualTo("hanako@example.com");
    }

    /**
     * <strong>氏名だけで登録できる。</strong> 住所と連絡先は引き渡しの当日までに
     * 分かれば足りる。必須にすると、氏名しか分かっていない段階で登録できず、
     * <strong>結局どこにも記録されない</strong>。
     */
    @Test
    void 氏名だけでも登録できる() throws Exception {
        UUID bookingId = 予約("PRELIMINARY");

        荷受人を登録する(bookingId, "受取花子", null, null)
                .andExpect(redirectedUrl("/bookings/" + bookingId));

        String name = jdbcTemplate.queryForObject(
                "SELECT consignee_name FROM cargo WHERE booking_id = ?", String.class, bookingId);
        assertThat(name).isEqualTo("受取花子");
    }

    /** 氏名は必須である（誰に渡すか分からない記録を残さない）。 */
    @Test
    void 氏名の無い登録は拒否される() throws Exception {
        UUID bookingId = 予約("PRELIMINARY");

        荷受人を登録する(bookingId, " ", null, null)
                .andExpect(redirectedUrl("/bookings/" + bookingId));

        String name = jdbcTemplate.queryForObject(
                "SELECT consignee_name FROM cargo WHERE booking_id = ?", String.class, bookingId);
        assertThat(name).isNull();
    }

    /** <strong>訂正も同じ操作である。</strong> 荷受人は輸送の直前まで変わりうる。 */
    @Test
    void 登録済みの荷受人を訂正できる() throws Exception {
        UUID bookingId = 予約("IN_TRANSIT");
        荷受人を登録する(bookingId, "受取花子", null, null);

        荷受人を登録する(bookingId, "受取次郎", "Long Beach, CA", null)
                .andExpect(redirectedUrl("/bookings/" + bookingId));

        String name = jdbcTemplate.queryForObject(
                "SELECT consignee_name FROM cargo WHERE booking_id = ?", String.class, bookingId);
        assertThat(name).isEqualTo("受取次郎");
    }

    /**
     * <strong>引き渡し済み以降は変えない。</strong> 引き渡した後に書き換えると、
     * <strong>誰に渡したかの記録が後から作り変えられる</strong>。
     */
    @Test
    void 引き渡し済みの予約の荷受人は変更できない() throws Exception {
        UUID bookingId = 予約("PRELIMINARY");
        荷受人を登録する(bookingId, "受取花子", null, null);
        jdbcTemplate.update(
                "UPDATE cargo SET booking_status = 'DELIVERED' WHERE booking_id = ?", bookingId);

        荷受人を登録する(bookingId, "書き換え太郎", null, null)
                .andExpect(redirectedUrl("/bookings/" + bookingId));

        String name = jdbcTemplate.queryForObject(
                "SELECT consignee_name FROM cargo WHERE booking_id = ?", String.class, bookingId);
        assertThat(name).isEqualTo("受取花子");
    }

    /** 引き渡し済みの予約詳細では、訂正のフォームそのものを出さない。 */
    @Test
    void 引き渡し済みの予約詳細には訂正フォームが出ない() throws Exception {
        UUID bookingId = 予約("DELIVERED");

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("変更できません")))
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("荷受人を訂正"))));
    }

    /** 登録した荷受人が予約詳細に表示される。 */
    @Test
    void 登録した荷受人が予約詳細に表示される() throws Exception {
        UUID bookingId = 予約("PRELIMINARY");
        荷受人を登録する(bookingId, "受取花子", "Los Angeles, CA", "hanako@example.com");

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("受取花子")))
                .andExpect(content().string(Matchers.containsString("Los Angeles, CA")));
    }
}
