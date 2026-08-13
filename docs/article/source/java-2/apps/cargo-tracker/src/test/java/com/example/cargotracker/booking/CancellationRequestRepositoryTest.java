package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CancellationFeeRate;
import com.example.cargotracker.booking.domain.model.aggregates.CancellationRequest;
import com.example.cargotracker.booking.domain.model.valueobjects.CancellationStatus;
import com.example.cargotracker.booking.domain.repository.CancellationRequestRepository;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * キャンセル申請の永続化（US30）。
 *
 * <p><strong>SQL の正しさを検証する場である</strong>（ADR-003）。H2 では書かない。
 *
 * <p>確かめるのは往復だけではない。<strong>決着していない申請が 1 件までであること</strong>、
 * <strong>決着した申請を二度更新できないこと</strong>、
 * <strong>却下した申請も履歴に残ること</strong>まで見る。
 */
@DisplayName("キャンセル申請の永続化（US30）")
class CancellationRequestRepositoryTest extends PostgreSQLIntegrationTestBase {

    private static final Location 大阪 = Location.of("JPOSA");
    private static final Instant 申請時刻 = Instant.parse("2026-08-10T01:00:00Z");
    private static final Instant 決定時刻 = Instant.parse("2026-08-10T02:00:00Z");

    @Autowired
    private CancellationRequestRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static CancellationRequest 申請(BookingId bookingId, String requestedBy) {
        return CancellationRequest.request(
                bookingId, "荷主都合", CancellationFeeRate.of(BookingStatus.IN_TRANSIT),
                requestedBy, 申請時刻);
    }

    /** 申請を保存して読み戻せる。 */
    @Test
    void 申請を保存して読み戻せる() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        long id = repository.save(申請(bookingId, "sales1"));

        CancellationRequest loaded = repository.findById(id).orElseThrow();

        assertThat(loaded.bookingId()).isEqualTo(bookingId);
        assertThat(loaded.reason()).isEqualTo("荷主都合");
        assertThat(loaded.requestedBy()).isEqualTo("sales1");
        assertThat(loaded.status()).isEqualTo(CancellationStatus.PENDING);
        assertThat(loaded.feeRate().value())
                .as("**申請時点の料率が残る**。承認時に計算し直さない")
                .isEqualByComparingTo(new java.math.BigDecimal("0.50"));
    }

    /** 承認（陸揚げ地つき）を保存して読み戻せる。 */
    @Test
    void 承認と陸揚げ地を保存して読み戻せる() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        long id = repository.save(申請(bookingId, "sales1"));

        CancellationRequest loaded = repository.findById(id).orElseThrow();
        loaded.approve(大阪, List.of(大阪), "tracker1", 決定時刻);
        assertThat(repository.update(loaded)).isTrue();

        CancellationRequest approved = repository.findById(id).orElseThrow();
        assertThat(approved.status()).isEqualTo(CancellationStatus.APPROVED);
        assertThat(approved.dischargeLocation()).isEqualTo(大阪);
        assertThat(approved.decision().by()).isEqualTo("tracker1");
    }

    /**
     * <strong>決着していない申請は 1 予約 1 件までである</strong>（ADR-018 と同型）。
     *
     * <p>2 件並ぶと、追跡管理者は同じ貨物について 2 度承認でき、
     * <strong>陸揚げ地が 2 か所決まる</strong>。
     */
    @Test
    void 決着していない申請は一予約一件までである() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        repository.save(申請(bookingId, "sales1"));

        assertThatThrownBy(() -> repository.save(申請(bookingId, "sales2")))
                .as("**口約束ではなく制約で守る**")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /**
     * <strong>却下したあとは改めて申請できる</strong>（US30）。
     *
     * <p>却下は「もう二度と申請させない」ことではない。事情が変われば
     * 荷主は改めて申請する。<strong>だから値オブジェクトにしない。</strong>
     */
    @Test
    void 却下したあとは改めて申請できる() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        long first = repository.save(申請(bookingId, "sales1"));

        CancellationRequest loaded = repository.findById(first).orElseThrow();
        loaded.reject("tracker1", 決定時刻, "代替の販売先を探すため輸送を続ける");
        assertThat(repository.update(loaded)).isTrue();

        long second = repository.save(申請(bookingId, "sales1"));

        assertThat(repository.findByBookingId(bookingId))
                .as("**却下も履歴に残る。** 却下したことも経緯である")
                .hasSize(2)
                .extracting(CancellationRequest::id)
                .containsExactly(second, first);
    }

    /**
     * <strong>決着した申請は二度更新できない</strong>（US30）。
     *
     * <p>ドメインが守っている条件を SQL にも置く。
     * <strong>集約を通らない経路が生まれても、決着した申請は動かない。</strong>
     */
    @Test
    void 決着した申請はDBも二度更新させない() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        long id = repository.save(申請(bookingId, "sales1"));

        CancellationRequest first = repository.findById(id).orElseThrow();
        CancellationRequest second = repository.findById(id).orElseThrow();

        first.approve(大阪, List.of(大阪), "tracker1", 決定時刻);
        assertThat(repository.update(first)).isTrue();

        second.reject("tracker2", 決定時刻, "後から却下する");
        assertThat(repository.update(second))
                .as("**別の担当者が先に決めていたら拒む**")
                .isFalse();

        assertThat(repository.findById(id).orElseThrow().status())
                .isEqualTo(CancellationStatus.APPROVED);
    }

    /**
     * <strong>承認待ち一覧は古い順である</strong>（US30）。
     *
     * <p>新しい順に並べると、<strong>古い申請がいつまでも下に残る</strong>。
     * 待たせている荷主から捌く。
     */
    @Test
    void 承認待ち一覧は古い順に並ぶ() {
        long older = repository.save(CancellationRequest.request(
                new BookingId(UUID.randomUUID()), "先に申請",
                CancellationFeeRate.of(BookingStatus.IN_TRANSIT), "sales1",
                Instant.parse("2026-01-01T00:00:00Z")));
        long newer = repository.save(CancellationRequest.request(
                new BookingId(UUID.randomUUID()), "後から申請",
                CancellationFeeRate.of(BookingStatus.IN_TRANSIT), "sales1",
                Instant.parse("2026-12-31T00:00:00Z")));

        List<Long> ids = repository.findPending().stream()
                .map(CancellationRequest::id)
                .toList();

        assertThat(ids.indexOf(older))
                .as("**待たせている申請から捌く**")
                .isLessThan(ids.indexOf(newer));
    }

    /**
     * <strong>承認したのに陸揚げ地が無い行を DB が拒む</strong>（US30）。
     *
     * <p>降ろす場所の決まらない承認は、<strong>荷役の現場に行き先の無い荷物を
     * 渡すことと同じである</strong>。ドメインが守っているが、
     * <strong>集約を通らない経路でも防ぐ</strong>。
     */
    @Test
    void 陸揚げ地の無い承認はDBが拒む() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        long id = repository.save(申請(bookingId, "sales1"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE booking_cancellation SET status = 'APPROVED' WHERE id = ?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /** 決着していない申請の有無を業務の言葉で判定できる。 */
    @Test
    void 決着していない申請の有無を判定できる() {
        BookingId bookingId = new BookingId(UUID.randomUUID());

        assertThat(repository.existsPendingFor(bookingId)).isFalse();
        long id = repository.save(申請(bookingId, "sales1"));
        assertThat(repository.existsPendingFor(bookingId)).isTrue();

        CancellationRequest loaded = repository.findById(id).orElseThrow();
        loaded.reject("tracker1", 決定時刻, "輸送を続ける");
        repository.update(loaded);

        assertThat(repository.existsPendingFor(bookingId))
                .as("決着すれば承認待ちではない")
                .isFalse();
    }
}
