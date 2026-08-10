package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.application.internal.queryservices.BillingQueryService;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import com.example.cargotracker.support.QueryCounter;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 請求の読み取りが件数に比例して問い合わせを増やさないこと（IT13 レビュー C4）。
 *
 * <p><strong>ダッシュボードは表示のたびに全件を走査していた。</strong>
 * {@code countPendingCargo()} が {@code findPendingCargo().size()} であり、
 * さらに 1 件ごとに請求書の有無を問い合わせていた。
 * <strong>経理担当者のトップページを開くコストが、請求対象の件数に比例して増える。</strong>
 *
 * <p><strong>時間で測らない。</strong> 経過時間のアサートは、遅いマシンでは偽陽性、
 * 速いマシンでは<strong>N+1 を残したままでも緑になる</strong>
 * （`feedback_timing-assertions-dont-discriminate` の型）。
 * <strong>「何回問い合わせたか」を数える。</strong>
 *
 * <p><strong>件数を変えて、増え方を見る。</strong> 1 件のときと 5 件のときで
 * 問い合わせ回数が変わらなければ、件数に比例していない。
 * 絶対値を固定すると、実装を少し変えるたびに落ちて意味を失う。
 */
@DisplayName("請求の読み取りの問い合わせ回数（C4）")
class BillingQueryEfficiencyTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private BillingQueryService queryService;

    @Autowired
    private QueryCounter queryCounter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 引取まで済んだ貨物を n 件用意する。 */
    private void 引取済みの貨物を用意する(int count, String prefix) {
        for (int i = 0; i < count; i++) {
            Long seq = jdbcTemplate.queryForObject(
                    "SELECT nextval('shipper_code_seq')", Long.class);
            UUID shipperId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO shipper (
                        id, shipper_code, shipper_type, name, email, phone,
                        address_country, address_postal_code, address_region,
                        address_city, address_street, discount_rate)
                    VALUES (?, ?, 'INDIVIDUAL', '効率テスト商事', ?, '06-1234-5678',
                            'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1', 0)
                    """, shipperId, "SHP-%06d".formatted(seq),
                    "efficiency-%d@example.com".formatted(seq));

            UUID bookingId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO cargo (
                        booking_id, shipper_id, cargo_type, weight,
                        origin_unlocode, destination_unlocode, arrival_deadline,
                        booking_status, routing_status, tracking_number)
                    VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'USLAX', CURRENT_DATE + 60,
                            'DELIVERED', 'ROUTED', ?)
                    """, bookingId, shipperId, "%s%04d".formatted(prefix, i));

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
        }
    }

    private int 問い合わせ回数(Runnable action) {
        queryCounter.reset();
        action.run();
        return queryCounter.count();
    }

    /**
     * <strong>請求対象の件数を数えるのに全件を組み立てない。</strong>
     *
     * <p>ダッシュボードは表示のたびにこれを呼ぶ。<strong>件数だけが要るのに
     * 一覧を作っていた</strong>ため、経理担当者のトップページが請求対象の件数に
     * 比例して重くなっていた。
     */
    @Test
    void 件数の取得が件数に比例しない() {
        引取済みの貨物を用意する(1, "TRK-20260701-1");
        int forOne = 問い合わせ回数(queryService::countPendingCargo);

        引取済みの貨物を用意する(4, "TRK-20260701-2");
        int forFive = 問い合わせ回数(queryService::countPendingCargo);

        assertThat(forFive)
                .as("1 件のとき %d 回、5 件のとき %d 回。**件数に比例している**",
                        forOne, forFive)
                .isEqualTo(forOne);
    }

    /**
     * <strong>請求対象一覧が 1 行ごとに問い合わせない。</strong>
     *
     * <p>1 行あたり「請求書の有無」「訂正の申請」「例外の有無」を個別に引いていた。
     */
    @Test
    void 請求対象一覧が件数に比例して問い合わせを増やさない() {
        引取済みの貨物を用意する(1, "TRK-20260702-1");
        int forOne = 問い合わせ回数(queryService::findPendingCargo);

        引取済みの貨物を用意する(4, "TRK-20260702-2");
        int forFive = 問い合わせ回数(queryService::findPendingCargo);

        assertThat(forFive)
                .as("1 件のとき %d 回、5 件のとき %d 回。**1 行ごとに問い合わせている**",
                        forOne, forFive)
                .isEqualTo(forOne);
    }

    /**
     * <strong>請求書一覧が 1 行ごとに問い合わせない。</strong>
     *
     * <p>{@code findByChargeStatus} で番号だけを取り、1 件ごとに集約と貨物を
     * 引き直していた（1 行あたり約 4 クエリ）。
     */
    @Test
    void 請求書一覧が件数に比例して問い合わせを増やさない() {
        引取済みの貨物を用意する(1, "TRK-20260703-1");
        queryService.findPendingCargo().forEach(this::請求書を作る);
        int forOne = 問い合わせ回数(() -> queryService.findInvoices(null));

        引取済みの貨物を用意する(4, "TRK-20260703-2");
        queryService.findPendingCargo().forEach(this::請求書を作る);
        int forFive = 問い合わせ回数(() -> queryService.findInvoices(null));

        assertThat(forFive)
                .as("1 件のとき %d 回、5 件のとき %d 回。**1 行ごとに問い合わせている**",
                        forOne, forFive)
                .isEqualTo(forOne);
    }

    /** 請求書を 1 件作る（一覧の行を用意するため）。 */
    private void 請求書を作る(
            com.example.cargotracker.billing.application.internal.queryservices
                    .PendingCargoView cargo) {
        jdbcTemplate.update("""
                INSERT INTO invoice (
                    invoice_number, booking_id, shipper_id,
                    base_amount_value, base_amount_currency,
                    discount_rate, tax_rate, tax_amount_value, tax_amount_currency,
                    total_amount_value, total_amount_currency,
                    charge_status, payment_status, version)
                VALUES ('INV-' || LPAD(nextval('invoice_number_seq')::text, 8, '0'),
                        ?, ?, 1000, 'JPY', 0, 0.1000, 100, 'JPY', 1100, 'JPY',
                        'CONFIRMED', 'PENDING', 0)
                """, UUID.fromString(cargo.bookingId()), UUID.randomUUID());
    }
}
