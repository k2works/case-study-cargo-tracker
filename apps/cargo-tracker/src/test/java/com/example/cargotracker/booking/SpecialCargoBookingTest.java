package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * US05: 危険物・冷凍貨物の予約を登録する。受入基準に 1:1 で対応させる。
 *
 * <p><strong>特別な情報を「入れられる」だけでは足りない。</strong> 危険物は法的要件を伴い、
 * 冷凍は温度を外すと貨物そのものが失われる。**申告の無い危険物を預かってしまう形を
 * 作らない**ことが本ストーリーの主眼である。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "sales", roles = "SALES")
@DisplayName("US05 危険物・冷凍貨物の予約を登録する")
class SpecialCargoBookingTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private String shipperCode;

    /** **件数と行の特定はこの荷主に絞る。** 同一クラス内の他のテストが残した行と混ざる。 */
    private UUID shipperId;

    @BeforeEach
    void 荷主を用意する() {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        shipperCode = "SHP-%06d".formatted(seq);
        shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '山田物産株式会社', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, shipperCode, "special-%d@example.com".formatted(seq));
    }

    private Map<String, String> 予約フォーム(String cargoType) {
        Map<String, String> values = new HashMap<>();
        values.put("shipperCode", shipperCode);
        values.put("cargoType", cargoType);
        values.put("weight", "1000");
        values.put("origin", "JPOSA");
        values.put("destination", "USLAX");
        values.put("arrivalDeadline", LocalDate.now(clock).plusDays(40).toString());
        return values;
    }

    private MockHttpServletRequestBuilder 登録する(Map<String, String> values) {
        var request = post("/bookings").with(csrf());
        values.forEach(request::param);
        return request;
    }

    private long 予約件数() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cargo WHERE shipper_id = ?", Long.class, shipperId);
    }

    /** 受入基準: 貨物種別「危険物」を選択すると、危険物申告の入力欄が表示される。 */
    @Test
    void 危険物を選ぶと申告の入力欄が出る() throws Exception {
        mockMvc.perform(get("/bookings/new/specification").param("cargoType", "HAZARDOUS"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("危険物クラス")))
                .andExpect(content().string(Matchers.containsString("UN 番号")))
                .andExpect(content().string(Matchers.containsString("正式輸送品名")));
    }

    /** 受入基準: 貨物種別「冷凍・冷蔵」を選択すると、温度管理条件の入力欄が表示される。 */
    @Test
    void 冷凍を選ぶと温度の入力欄が出る() throws Exception {
        mockMvc.perform(get("/bookings/new/specification").param("cargoType", "REFRIGERATED"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("最低温度")))
                .andExpect(content().string(Matchers.containsString("最高温度")));
    }

    /** 一般貨物では特別な入力欄を出さない。**押せない欄を見せない。** */
    @Test
    void 一般貨物では特別な入力欄を出さない() throws Exception {
        mockMvc.perform(get("/bookings/new/specification").param("cargoType", "GENERAL"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("危険物クラス"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("最低温度"))));
    }

    /**
     * <strong>種別を選び直しても入力済みの申告は消えない。</strong>
     *
     * <p>UN 番号は書類から転記する値である。種別を触るたびに消えると、
     * **同じ値を二度入力することになる**（危険物 → 冷凍 → 危険物 は実際に起きる）。
     */
    @Test
    void 種別を選び直しても入力済みの申告は残る() throws Exception {
        mockMvc.perform(get("/bookings/new/specification")
                        .param("cargoType", "HAZARDOUS")
                        .param("hazardClass", "3")
                        .param("unNumber", "UN1263")
                        .param("properShippingName", "PAINT"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("UN1263")))
                .andExpect(content().string(Matchers.containsString("PAINT")));
    }

    /** 受入基準: 危険物申告を入力して登録できる。 */
    @Test
    void 危険物を申告つきで登録できる() throws Exception {
        var values = 予約フォーム("HAZARDOUS");
        values.put("hazardClass", "3");
        values.put("unNumber", "UN1263");
        values.put("properShippingName", "PAINT");

        mockMvc.perform(登録する(values)).andExpect(status().is3xxRedirection());

        var row = jdbcTemplate.queryForMap("""
                SELECT hazardous_class, un_number, proper_shipping_name
                  FROM cargo WHERE shipper_id = ?
                """, shipperId);
        assertThat(row).containsEntry("hazardous_class", "3");
        assertThat(row).containsEntry("un_number", "UN1263");
        assertThat(row).containsEntry("proper_shipping_name", "PAINT");
    }

    /**
     * 受入基準: <strong>危険物申告の入力が必須である。</strong>
     *
     * <p>**申告の無い危険物を預かると、法的要件を満たさないまま輸送が始まる。**
     * 画面から欄を消すだけでは足りず、細工した送信でも通してはならない。
     */
    @Test
    void 申告の無い危険物は登録できない() throws Exception {
        long before = 予約件数();

        mockMvc.perform(登録する(予約フォーム("HAZARDOUS")))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/form"))
                .andExpect(content().string(Matchers.containsString("危険物申告")));

        assertThat(予約件数()).isEqualTo(before);
    }

    /** UN 番号だけが欠けていても登録できない。**3 項目そろって初めて申告である。** */
    @Test
    void 番号だけを欠いた危険物は登録できない() throws Exception {
        long before = 予約件数();
        var values = 予約フォーム("HAZARDOUS");
        values.put("hazardClass", "3");
        values.put("properShippingName", "PAINT");

        mockMvc.perform(登録する(values)).andExpect(status().isOk());

        assertThat(予約件数()).isEqualTo(before);
    }

    /** 受入基準: 温度管理条件を入力して登録できる。 */
    @Test
    void 冷凍を温度つきで登録できる() throws Exception {
        var values = 予約フォーム("REFRIGERATED");
        values.put("minTemperature", "-25.0");
        values.put("maxTemperature", "-18.0");
        values.put("temperatureUnit", "CELSIUS");

        mockMvc.perform(登録する(values)).andExpect(status().is3xxRedirection());

        var row = jdbcTemplate.queryForMap("""
                SELECT min_temperature, max_temperature, temperature_unit
                  FROM cargo WHERE shipper_id = ?
                """, shipperId);
        assertThat(((java.math.BigDecimal) row.get("min_temperature")))
                .isEqualByComparingTo("-25.0");
        assertThat(((java.math.BigDecimal) row.get("max_temperature")))
                .isEqualByComparingTo("-18.0");
        assertThat(row).containsEntry("temperature_unit", "CELSIUS");
    }

    /** 受入基準: 温度管理条件の入力が必須である。 */
    @Test
    void 温度の無い冷凍は登録できない() throws Exception {
        long before = 予約件数();

        mockMvc.perform(登録する(予約フォーム("REFRIGERATED")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("温度管理条件")));

        assertThat(予約件数()).isEqualTo(before);
    }

    /**
     * <strong>最低温度が最高温度を上回る指定は受け付けない。</strong>
     *
     * <p>入れ違いは打ち間違いとして日常的に起きる。通してしまうと、
     * **どの温度帯でも条件を満たさない**貨物を預かることになる。
     */
    @Test
    void 最低温度が最高温度を上回る指定は登録できない() throws Exception {
        long before = 予約件数();
        var values = 予約フォーム("REFRIGERATED");
        values.put("minTemperature", "-18.0");
        values.put("maxTemperature", "-25.0");
        values.put("temperatureUnit", "CELSIUS");

        mockMvc.perform(登録する(values)).andExpect(status().isOk());

        assertThat(予約件数()).isEqualTo(before);
    }

    /**
     * <strong>一般貨物に特別な情報は付かない。</strong>
     *
     * <p>種別を変えた後の入力が残っていても、**種別と申告の組み合わせは
     * 集約が守る**（危険物でないのに申告がある形を作らせない）。
     */
    @Test
    void 一般貨物に危険物申告を送っても保存されない() throws Exception {
        var values = 予約フォーム("GENERAL");
        values.put("hazardClass", "3");
        values.put("unNumber", "UN1263");
        values.put("properShippingName", "PAINT");

        mockMvc.perform(登録する(values)).andExpect(status().is3xxRedirection());

        var row = jdbcTemplate.queryForMap(
                "SELECT hazardous_class FROM cargo WHERE shipper_id = ?", shipperId);
        assertThat(row.get("hazardous_class")).isNull();
    }

    /** 予約詳細に特別な情報が表示される。**記録しても見えなければ確認できない。** */
    @Test
    void 予約詳細に危険物申告が表示される() throws Exception {
        var values = 予約フォーム("HAZARDOUS");
        values.put("hazardClass", "3");
        values.put("unNumber", "UN1263");
        values.put("properShippingName", "PAINT");
        mockMvc.perform(登録する(values));

        String bookingId = jdbcTemplate.queryForObject(
                "SELECT CAST(booking_id AS VARCHAR) FROM cargo WHERE shipper_id = ?",
                String.class, shipperId);

        mockMvc.perform(get("/bookings/{id}", bookingId))
                .andExpect(content().string(Matchers.containsString("UN1263")))
                .andExpect(content().string(Matchers.containsString("PAINT")));
    }
}
