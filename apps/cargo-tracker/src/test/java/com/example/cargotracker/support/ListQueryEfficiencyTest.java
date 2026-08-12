package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.internal.queryservices
        .BookingNotificationQueryService;
import com.example.cargotracker.handling.application.internal.queryservices.CorrectionQueryService;
import com.example.cargotracker.handling.application.internal.queryservices.CustomsQueryService;
import com.example.cargotracker.routing.application.internal.queryservices
        .RouteProposalQueryService;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingBookingId;
import com.example.cargotracker.security.application.internal.queryservices
        .LockedAccountQueryService;
import com.example.cargotracker.tracking.application.internal.queryservices
        .TrackingExceptionQueryService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 一覧を返すクエリサービスが<strong>件数に比例して問い合わせを増やさない</strong>こと（R5）。
 *
 * <p>IT16 で {@code ListQueryMeasurementTest} を入れ、「一覧を返すクエリサービスには
 * 問い合わせ回数の計測がある」ことを検査にした。その時点で
 * <strong>計測が無いものが 6 件あり</strong>、名前で据え置いた（{@code NOT_MEASURED_YET}）。
 * IT17 の R5 でそれを返す。
 *
 * <p><strong>時間で測らない。</strong> 経過時間のアサートは遅いマシンでは偽陽性、
 * 速いマシンでは<strong>N+1 を残したままでも緑になる</strong>。
 * <strong>「何回問い合わせたか」を数える。</strong>
 *
 * <p><strong>件数を変えて、増え方を見る。</strong> 1 件のときと 5 件のときで
 * 問い合わせ回数が変わらなければ、件数に比例していない。絶対値を固定すると、
 * 実装を少し変えるたびに落ちて意味を失う。
 *
 * <p><strong>0 件では判別できない。</strong> 空の一覧はどんな実装でも 1 回で返る。
 * 「1 件でも問い合わせが起きること」を先に確かめてから、増え方を見る。
 */
@DisplayName("一覧の問い合わせ回数が件数に比例しない（R5）")
class ListQueryEfficiencyTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueryCounter queryCounter;

    @Autowired
    private Clock clock;

    @Autowired
    private BookingNotificationQueryService notifications;

    @Autowired
    private CorrectionQueryService corrections;

    @Autowired
    private CustomsQueryService customs;

    @Autowired
    private LockedAccountQueryService lockedAccounts;

    @Autowired
    private RouteProposalQueryService routeProposals;

    @Autowired
    private TrackingExceptionQueryService exceptions;

    @Autowired
    private com.example.cargotracker.estimation.application.internal.queryservices
            .EstimateQueryService estimates;

    /**
     * 件数を 1 → 5 に増やしても問い合わせ回数が増えないことを見る。
     *
     * @param seed  n 件目のデータを用意する処理
     * @param query 計測する読み取り
     */
    private void 件数を増やしても問い合わせが増えない(Consumer<Integer> seed, Runnable query) {
        seed.accept(0);
        int oneRow = 問い合わせ回数(query);
        assertThat(oneRow)
                .as("1 件でも問い合わせが起きること（0 回ならこの計測は何も見ていない）")
                .isPositive();

        for (int i = 1; i < 5; i++) {
            seed.accept(i);
        }
        int fiveRows = 問い合わせ回数(query);

        assertThat(fiveRows)
                .as("""
                        一覧の問い合わせ回数が件数に比例しています（R5）。

                        **待ち行列が伸びるほど遅くなります** — いちばん混んでいるときに、
                        いちばん遅い。1 件ずつ引き直さず、まとめて引いてください。""")
                .isEqualTo(oneRow);
    }

    private int 問い合わせ回数(Runnable action) {
        queryCounter.reset();
        action.run();
        return queryCounter.count();
    }

    // --- 予約の通知（IT8 以前）---

    @Test
    void 予約の通知一覧は件数に比例しない() {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("通知計測商事")
                .status("CONFIRMED", "ROUTED")
                .insert();
        String bookingId = cargo.bookingId().toString();

        件数を増やしても問い合わせが増えない(
                i -> 通知を残す(cargo.bookingId(), i),
                () -> notifications.findByBookingId(bookingId));
    }

    private void 通知を残す(UUID bookingId, int index) {
        jdbcTemplate.update("""
                INSERT INTO booking_notification (
                    booking_id, notification_type, recipient_email, content,
                    sent_at, sent_by, result, failure_reason)
                VALUES (?, 'ROUTE_CONFIRMED', ?, ?,
                        TIMESTAMP WITH TIME ZONE '2026-04-20 09:00:00+09',
                        'sales', 'SUCCEEDED', NULL)
                """, bookingId, "shipper%d@example.com".formatted(index),
                "確定した経路をお知らせします。（%d）".formatted(index));
    }

    // --- 訂正・取り消しの承認待ち（IT12 以前）---

    @Test
    void 訂正の承認待ち一覧は件数に比例しない() {
        件数を増やしても問い合わせが増えない(
                this::訂正の申請を用意する,
                () -> corrections.findPending());
    }

    private void 訂正の申請を用意する(int index) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("訂正計測商事")
                .status("DELIVERED", "ROUTED")
                .trackingNumber("TRK-CORR-%04d".formatted(index))
                .insert();
        jdbcTemplate.update("""
                INSERT INTO handling_activity (
                    booking_id, event_type, event_completion_time, location_unlocode,
                    operator_name, version, claim_confirmation_method,
                    claim_confirmation_code, claim_consignee_name, tracking_number)
                VALUES (?, 'CLAIM', TIMESTAMP WITH TIME ZONE '2026-04-20 09:00:00+09',
                        'USLAX', '荷役太郎', 0, 'CLAIM_CODE', ?, '受取次郎', ?)
                """, cargo.bookingId(), "CLM-%04d".formatted(index), cargo.trackingNumber());
        Long activityId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM handling_activity WHERE booking_id = ?",
                Long.class, cargo.bookingId());
        jdbcTemplate.update("""
                INSERT INTO handling_correction (
                    handling_activity_id, request_type, reason,
                    requested_by, requested_at, status, version)
                VALUES (?, 'CANCEL', '引取の記録が誤りでした',
                        '荷役太郎', TIMESTAMP WITH TIME ZONE '2026-04-21 09:00:00+09',
                        'PENDING', 0)
                """, activityId);
    }

    // --- 通関申告の一覧（IT11 以前）---

    @Test
    void 通関申告の一覧は件数に比例しない() {
        件数を増やしても問い合わせが増えない(
                this::通関の申告を用意する,
                () -> customs.search(null, null));
    }

    private void 通関の申告を用意する(int index) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("通関計測商事")
                .status("IN_TRANSIT", "ROUTED")
                .trackingNumber("TRK-CUST-%04d".formatted(index))
                .insert();
        jdbcTemplate.update("""
                INSERT INTO handling_activity (
                    booking_id, event_type, event_completion_time, location_unlocode,
                    operator_name, version, tracking_number)
                VALUES (?, 'CUSTOMS', TIMESTAMP WITH TIME ZONE '2026-04-20 09:00:00+09',
                        'USSEA', '港湾太郎', 0, ?)
                """, cargo.bookingId(), cargo.trackingNumber());
        Long activityId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM handling_activity WHERE booking_id = ?",
                Long.class, cargo.bookingId());
        jdbcTemplate.update("""
                INSERT INTO customs_declaration (
                    handling_activity_id, declaration_number, status, declared_at)
                VALUES (?, ?, 'PENDING',
                        TIMESTAMP WITH TIME ZONE '2026-04-20 10:00:00+09')
                """, activityId, "DEC-CUST-%04d".formatted(index));
    }

    // --- ロック済みアカウント（IT5 以前）---

    @Test
    void ロック済みアカウントの一覧は件数に比例しない() {
        件数を増やしても問い合わせが増えない(
                this::ロック中の利用者を用意する,
                () -> lockedAccounts.findLocked());
    }

    private void ロック中の利用者を用意する(int index) {
        String username = "locked-eff-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO users (username, email, password, enabled,
                                   failed_attempts, locked_until)
                VALUES (?, ?, '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe',
                        TRUE, ?, ?)
                """,
                username, username + "@example.com", 5 + index,
                java.sql.Timestamp.from(clock.instant().plus(30, ChronoUnit.MINUTES)));
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role) "
                        + "SELECT id, 'ROLE_HANDLER' FROM users WHERE username = ?", username);
    }

    // --- 経路候補（IT4 以前）---

    /**
     * <strong>候補の数を増やしても問い合わせは増えない。</strong>
     *
     * <p>他の 5 件と違い、これは<strong>一覧の行数ではなく 1 件の中の候補数</strong>で
     * 見る。経路割り当ての画面は予約 1 件を開くが、その中に候補が並ぶ。
     */
    @Test
    void 経路候補の読み取りは候補数に比例しない() {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("経路計測商事")
                .status("CONFIRMED", "NOT_ROUTED")
                .insert();
        RoutingBookingId bookingId = new RoutingBookingId(cargo.bookingId());
        Long proposalId = 経路候補の入れ物を用意する(cargo.bookingId());

        件数を増やしても問い合わせが増えない(
                i -> 候補を足す(proposalId, i),
                () -> routeProposals.find(bookingId));
    }

    private Long 経路候補の入れ物を用意する(UUID bookingId) {
        jdbcTemplate.update("""
                INSERT INTO booking_route_proposal (
                    booking_id, origin_unlocode, destination_unlocode,
                    arrival_deadline, original_arrival_deadline,
                    max_transit_count, calculation_count, candidate_count,
                    cargo_type, weight, version)
                VALUES (?, 'JPOSA', 'USLAX', DATE '2026-06-01', DATE '2026-06-01',
                        3, 1, 5, 'GENERAL', 1000, 0)
                """, bookingId);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM booking_route_proposal WHERE booking_id = ?",
                Long.class, bookingId);
    }

    private void 候補を足す(Long proposalId, int index) {
        jdbcTemplate.update("""
                INSERT INTO proposed_route (
                    proposal_id, priority, voyage_number, departure_date, arrival_date,
                    transit_days, estimated_cost_value, estimated_cost_currency,
                    capacity_available, hazardous_allowed, refrigerated_allowed,
                    deadline_satisfied, transit_ports, boarding_index, landing_index)
                VALUES (?, ?, ?, TIMESTAMP WITH TIME ZONE '2026-04-10 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-28 09:00:00+09',
                        18, 120000, 'JPY', TRUE, TRUE, TRUE, TRUE, '', 0, 0)
                """, proposalId, index + 1, "V%04d".formatted(index + 1));
    }

    // --- 見積（US01。IT18）---

    /**
     * <strong>見積一覧は件数に比例しない</strong>（US01）。
     *
     * <p>最安候補は SQL で 1 回に畳んでいる。1 件ごとに候補を引き直すと、
     * <strong>営業担当者が案件を探すたびに遅くなる</strong>。
     */
    @Test
    void 見積の一覧は件数に比例しない() {
        件数を増やしても問い合わせが増えない(
                this::見積を用意する,
                () -> estimates.findAll());
    }

    private void 見積を用意する(int index) {
        jdbcTemplate.update("""
                INSERT INTO estimate (
                    estimate_id, origin_unlocode, destination_unlocode,
                    arrival_deadline, cargo_type, weight_kg, status, version)
                VALUES (?, 'JPOSA', 'USLAX', CURRENT_DATE + 60, 'GENERAL', 1000, 'CREATED', 0)
                """, UUID.randomUUID());
        Long estimateId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM estimate", Long.class);
        // **候補は 1 件の見積に複数ぶら下がる。** 1 件だけだと畳めていなくても緑になる
        for (int i = 0; i < 3; i++) {
            jdbcTemplate.update("""
                    INSERT INTO route_candidate (
                        estimate_id, voyage_number, transit_port, transit_days,
                        estimated_cost_value, estimated_cost_currency, priority)
                    VALUES (?, ?, 'JPTYO', 18, ?, 'JPY', ?)
                    """, estimateId, "V%04d".formatted(index * 10 + i),
                    120000 + i * 1000, i);
        }
    }

    // --- 未解決の例外（IT10 以前）---

    @Test
    void 例外の一覧は件数に比例しない() {
        件数を増やしても問い合わせが増えない(
                this::例外を用意する,
                () -> exceptions.search(true, false));
    }

    private void 例外を用意する(int index) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("例外計測商事")
                .status("IN_TRANSIT", "ROUTED")
                .trackingNumber("TRK-EXC-%04d".formatted(index))
                .insert();
        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'ONBOARD_CARRIER', 0)
                """, cargo.trackingNumber(), cargo.bookingId());
        Long trackingId = jdbcTemplate.queryForObject(
                "SELECT id FROM tracking_activity WHERE tracking_number = ?",
                Long.class, cargo.trackingNumber());
        jdbcTemplate.update("""
                INSERT INTO tracking_exception_event (
                    tracking_id, exception_type, occurred_at, escalation_flag,
                    status_before, description, location_unlocode)
                VALUES (?, 'DELAY', TIMESTAMP WITH TIME ZONE '2026-04-10 09:00:00+09',
                        FALSE, 'ONBOARD_CARRIER', ?, 'JPOSA')
                """, trackingId, "遅延が発生しました（%d）".formatted(index));
    }
}
