package com.example.bookingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.CargoRestoration;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoStatus;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.TrackingNumber;
import com.example.bookingms.domain.model.TransportStatus;
import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 荷役のイベントで予約を進める（[ADR-025] 決定 1）。
 *
 * <p><strong>bookingms は自分では「輸送中」を知らない。</strong>荷役の記録が一次情報で
 * あり、予約一覧はこれが無いと、船に載った貨物を「受領待ち」と出し続ける。
 */
@DisplayName("荷役のイベントで予約を進める")
class AdvanceBookingUseCaseTest {

    private static final String TRACKING = "TRK-20260823-0001";
    private static final Instant AT = Instant.parse("2026-09-05T00:00:00Z");

    private Cargo stored = tracked();

    /** 書き込まれた状態。**何回書いたか**を数えるために持つ。 */
    private final List<BookingStatus> written = new ArrayList<>();

    private static Cargo tracked() {
        return CargoRestoration.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                new CargoStatus(BookingStatus.TRACKING_ISSUED, TransportStatus.NOT_RECEIVED,
                        RoutingStatus.ROUTED),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(Location.of("JPTYO", "Tokyo"),
                        Location.of("USLAX", "Los Angeles"),
                        LocalDate.of(2030, Month.SEPTEMBER, 1),
                        LocalDate.of(2030, Month.SEPTEMBER, 20)),
                (CargoItinerary) null, null, TrackingNumber.of(TRACKING));
    }

    private final CargoRepository cargoes = new StubCargoes();

    private final AdvanceBookingUseCase useCase = new AdvanceBookingUseCase(cargoes);

    private void advance(String type, String locationUnLocode) {
        useCase.advance(TRACKING, type, locationUnLocode, AT, false);
    }

    @Test
    @DisplayName("積込で輸送中になり、行に残る")
    void advancesToInTransitOnLoad() {
        advance("LOAD", "JPTYO");

        assertThat(stored.bookingStatus()).isEqualTo(BookingStatus.IN_TRANSIT);
        assertThat(stored.lastHandlingLocation()).contains("JPTYO");
        assertThat(written).containsExactly(BookingStatus.IN_TRANSIT);
    }

    /**
     * <strong>同じ荷役が 2 回届いても 1 度しか書かない。</strong>
     *
     * <p>再試行がある以上、同じイベントが 2 回届くのは普通のことである。毎回書くと、
     * 何も変わっていない更新が記録に積まれる。
     */
    @Test
    @DisplayName("同じ荷役が 2 回届いても、2 回目は書き込まない")
    void isIdempotent() {
        advance("LOAD", "JPTYO");
        written.clear();

        advance("LOAD", "JPTYO");

        assertThat(written)
                .as("何も変わっていないのに書き込んでいる")
                .isEmpty();
    }

    /**
     * <strong>巻き戻さない。</strong>
     *
     * <p>デッドレターからの送り直しで、荷役の届く順は入れ替わる。あとから届いた古い作業で
     * 予約が輸送中へ戻ると、荷主は「配送完了だったはずの貨物が輸送中に戻っている」を見る。
     */
    @Test
    @DisplayName("あとから古い荷役が届いても、巻き戻らない")
    void neverRegressesTheBookingStatus() {
        advance("LOAD", "JPTYO");
        advance("CLAIM", "USLAX");
        written.clear();

        advance("LOAD", "JPTYO");

        assertThat(stored.bookingStatus()).isEqualTo(BookingStatus.DELIVERED);
        assertThat(written).isEmpty();
    }

    /**
     * <strong>知らない追跡番号では止まらない。</strong>
     *
     * <p>例外にすると、後続の荷役イベントも処理されなくなる。この購読が守るのは
     * 「予約一覧の見え方」であり、止めるほどのものではない（AdvanceTracking と同じ立場）。
     */
    @Test
    @DisplayName("知らない追跡番号では止まらない")
    void doesNotFailForAnUnknownTrackingNumber() {
        assertThatCode(() -> useCase.advance("TRK-20260823-9999", "LOAD", "JPTYO", AT, false))
                .doesNotThrowAnyException();
        assertThat(written).isEmpty();
    }

    /** 受領では動かない。まだ港にあり、船に載っていない。 */
    @Test
    @DisplayName("受領では予約の状態を動かさない")
    void doesNotAdvanceOnReceive() {
        advance("RECEIVE", "JPTYO");

        assertThat(stored.bookingStatus()).isEqualTo(BookingStatus.TRACKING_ISSUED);
        assertThat(written).isEmpty();
    }

    private final class StubCargoes implements CargoRepository {

        @Override
        public Cargo save(Cargo cargo) {
            written.add(cargo.bookingStatus());
            stored = cargo;
            return cargo;
        }

        @Override
        public Optional<Cargo> findById(Long id) {
            return Optional.of(stored);
        }

        @Override
        public Optional<com.example.bookingms.application.port.CargoSummary> findByBookingId(
                String bookingId) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public Optional<com.example.bookingms.application.port.CargoSummary> findByTrackingNumber(
                String trackingNumber) {
            return TRACKING.equals(trackingNumber)
                    ? Optional.of(new com.example.bookingms.application.port.CargoSummary(
                            stored, "荷主"))
                    : Optional.empty();
        }

        @Override
        public List<com.example.bookingms.application.port.CargoSummary> search(CargoType type,
                String keyword, java.util.Collection<RoutingStatus> routingStatuses,
                BookingStatus bookingStatus, int limit) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public String nextTrackingNumber() {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public long count(CargoType type, String keyword,
                java.util.Collection<RoutingStatus> routingStatuses,
                BookingStatus bookingStatus) {
            throw new UnsupportedOperationException("この検査では使わない");
        }
    }

    /**
     * <strong>予定ルート外の荷役で、誤配として記録する</strong>（US28-2・[ADR-026] 決定 1）。
     *
     * <p><strong>判定はしない。</strong>{@code offRoute} は handlingms が旅程と作業場所を
     * 照合した結果である——ここで判定し直すと、旅程の写しをもう 1 つ持つことになり、
     * 片方だけが古い旅程で判定する状態が生まれる。
     */
    @Test
    @DisplayName("予定ルート外の荷役で、予約が誤配になる")
    void marksTheCargoAsMisrouted() {
        useCase.advance(TRACKING, "UNLOAD", "SGSIN", AT, true);

        assertThat(stored.isMisrouted())
                .as("誤配が記録されていない。経路設計者は組み直す対象に気づけない")
                .isTrue();
        assertThat(stored.misroute().orElseThrow().locationUnLocode()).isEqualTo("SGSIN");
        assertThat(stored.routingStatus()).isEqualTo(RoutingStatus.MISROUTED);
    }

    /**
     * <strong>誤配でも状態は進む。</strong>
     *
     * <p>予定外の港で降ろされても、荷役は起きている。進めないと、貨物が動いているのに
     * 予約は「受領待ち」のままになる——**IT9 まで 7 イテレーション続いた形**である。
     */
    @Test
    @DisplayName("誤配でも、荷役に応じた状態は進む")
    void stillAdvancesTheStatusWhenMisrouted() {
        useCase.advance(TRACKING, "LOAD", "SGSIN", AT, true);

        assertThat(stored.bookingStatus())
                .as("誤配だと状態が進まない。貨物は動いているのに予約は受領待ちのまま")
                .isEqualTo(BookingStatus.IN_TRANSIT);
        assertThat(stored.isMisrouted()).isTrue();
    }

    /** 予定どおりの荷役では、誤配にしない。 */
    @Test
    @DisplayName("予定どおりの荷役は誤配にしない")
    void doesNotMarkPlannedHandlingAsMisrouted() {
        advance("LOAD", "JPTYO");

        assertThat(stored.isMisrouted())
                .as("予定どおりの荷役が誤配になっている。経路設計者の一覧が誤配で埋まる")
                .isFalse();
    }
}
