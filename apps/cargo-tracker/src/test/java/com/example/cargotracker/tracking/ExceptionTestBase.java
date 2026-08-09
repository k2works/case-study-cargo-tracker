package com.example.cargotracker.tracking;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 例外のテストが使う土台（US19 / US20）。
 *
 * <p>港は既存のテストが使っていない組み合わせ（NLRTM / DEHAM）を使う（IT9 の Try T7）。
 * 別のテストが使う港と重ねると、一覧の並びが混ざって
 * <strong>原因でないテストが落ちる</strong>。
 *
 * <p>日時は {@link Clock} 相対で作る（Try T6。<strong>絶対日付を書かない</strong>）。
 * 固定日を過ぎると一斉に落ちる。
 */
abstract class ExceptionTestBase extends PostgreSQLIntegrationTestBase {

    /** 発生場所。**既存のテストが使っていない港である。** */
    protected static final String 発生港 = "NLRTM";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected Clock clock;

    /** 追跡中の貨物を用意する。**荷主名は一覧に出るため実在の値を入れる。** */
    protected String 追跡中の貨物(String trackingNumber, String status) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '例外テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq), "exc-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number)
                VALUES (?, ?, 'GENERAL', 1000.000, 'NLRTM', 'DEHAM', ?,
                        'IN_TRANSIT', 'NOT_ROUTED', ?)
                """, bookingId, shipperId, LocalDate.now(clock).plusDays(60), trackingNumber);

        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version,
                    destination_unlocode, estimated_arrival_date)
                VALUES (?, ?, ?, 0, 'DEHAM', ?)
                """, trackingNumber, bookingId, status, LocalDate.now(clock).plusDays(30));
        return trackingNumber;
    }

    /** **未来にしない。** 未来の発生日時は受け付けない。 */
    protected String 発生日時() {
        return LocalDateTime.now(clock).minusHours(1).withSecond(0).withNano(0).toString();
    }

    /**
     * 例外を登録する。
     *
     * <p><strong>追跡管理者として送る</strong>ことを明示する。管理者のテストから
     * 呼ぶと、クラスに付けた {@code @WithMockUser} ではなくそちらの認証情報が効き、
     * <strong>403 で黙って何も登録されない</strong>
     * （private メソッドに {@code @WithMockUser} を付けても効かない）。
     */
    protected ResultActions 例外を登録する(String trackingNumber, String type) throws Exception {
        return mockMvc.perform(post("/tracking/exceptions")
                .param("trackingNumber", trackingNumber)
                .param("exceptionType", type)
                .param("location", 発生港)
                .param("occurredAt", 発生日時())
                .param("description", "現地で足止めされています")
                .with(user("tracker").roles("TRACKER"))
                .with(csrf()));
    }

    /** 例外を解決する。 */
    protected ResultActions 例外を解決する(long id, String number, String notes) throws Exception {
        return mockMvc.perform(post("/tracking/exceptions/{id}/resolve", id)
                .param("trackingNumber", number)
                .param("resolutionNotes", notes)
                .with(csrf()));
    }

    protected Map<String, Object> 例外の行(String trackingNumber) {
        return jdbcTemplate.queryForMap("""
                SELECT e.* FROM tracking_exception_event e
                  JOIN tracking_activity t ON t.id = e.tracking_id
                 WHERE t.tracking_number = ?
                """, trackingNumber);
    }

    /** 例外の ID。解決は ID で指す。 */
    protected long 例外の識別子(String trackingNumber) {
        return ((Number) 例外の行(trackingNumber).get("id")).longValue();
    }
}
