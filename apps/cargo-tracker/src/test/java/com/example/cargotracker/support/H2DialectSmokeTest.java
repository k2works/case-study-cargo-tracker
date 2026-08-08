package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteProposalQueryService;
import com.example.cargotracker.routing.domain.model.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperQueryService;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * すべてのクエリが <strong>H2 でも解釈できる SQL</strong> であることを確かめる。
 *
 * <p>ADR-003 は「SQL の正しさを H2 で判断しない」と定めており、それは正しい。
 * <strong>だが逆方向の失敗が繰り返し起きている。</strong> PostgreSQL でしか解釈できない
 * SQL を書くと、Testcontainers のテストは全部緑のまま、
 * <strong>ローカル起動でだけ画面が 500 になる</strong>。
 *
 * <p>IT2 と IT3 で 3 回踏んだ。
 *
 * <ol>
 *   <li>{@code setval}（マイグレーション）— ローカル起動が落ちた</li>
 *   <li>{@code INTERVAL 'N days'}（動作確認用データ）— ローカル起動が落ちた</li>
 *   <li>{@code LATERAL}（航路一覧のクエリ）— 画面が 500 になった</li>
 * </ol>
 *
 * <p>いずれも「画面を触ろうとした瞬間」まで分からなかった。**H2 を使う目的は
 * 画面を触るサイクルを短くすることであり、H2 で動かない SQL を書くとその目的が消える。**
 *
 * <p>本テストは<strong>結果の正しさを検証しない</strong>。それは Testcontainers の
 * テストの仕事である。ここで見るのは「解釈できるか」だけであり、
 * だからこそデータが 1 件も無くても意味がある。
 */
@SpringBootTest
@ActiveProfiles("h2-dialect")
@DisplayName("H2 でも解釈できる SQL であること")
class H2DialectSmokeTest {

    @Autowired
    private ShipperQueryService shipperQueryService;

    @Autowired
    private BookingQueryService bookingQueryService;

    @Autowired
    private VoyageQueryService voyageQueryService;

    @Autowired
    private RouteProposalQueryService routeProposalQueryService;

    @Autowired
    private VoyageRepository voyageRepository;

    @Autowired
    private com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository
            trackingRepository;

    @Autowired
    private com.example.cargotracker.handling.domain.repository
            .HandlingActivityRepository handlingRepository;

    @Autowired
    private com.example.cargotracker.booking.domain.repository.CargoRepository cargoRepository;

    @Autowired
    private com.example.cargotracker.tracking.infrastructure.repositories.TrackingSequence
            trackingSequence;

    @Autowired
    private com.example.cargotracker.shipper.infrastructure.repositories.ShipperMapper
            shipperMapper;

    @Test
    void 荷主の検索が実行できる() {
        assertThatCode(() -> shipperQueryService.search("山田", PageRequest.of(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> shipperQueryService.search(null, PageRequest.of(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> shipperQueryService.findById("11111111-1111-4111-8111-111111111111"))
                .doesNotThrowAnyException();
        // **ROUND と CASE を含む**（US03 の契約割引率）。どちらも方言差の出る構文であり、
        // 荷主一覧は営業担当者が最初に開く画面である
    }

    @Test
    void 貨物予約の検索が実行できる() {
        assertThatCode(() -> bookingQueryService.search(
                "JPOSA", "USLAX", "PRELIMINARY", null, PageRequest.of(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> bookingQueryService.search(
                null, null, null, null, PageRequest.of(1)))
                .doesNotThrowAnyException();
        // **追跡番号の部分一致は方言差が出る**（PostgreSQL の ILIKE は H2 の
        // 互換モードで挙動が揃わない）。UPPER + LIKE + || で書いており、
        // 連結演算子も方言差の出る構文である
        assertThatCode(() -> bookingQueryService.search(
                null, null, null, "trk-2026", PageRequest.of(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> bookingQueryService.findAwaitingRouting(PageRequest.of(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void 航海の検索が実行できる() {
        assertThatCode(() -> voyageQueryService.search(
                "JPOSA", "USLAX",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                RoutingCargoType.GENERAL, PageRequest.of(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> voyageQueryService.search(
                null, null, null, null, null, PageRequest.of(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void 航海詳細の読み取りが実行できる() {
        assertThatCode(() -> voyageQueryService.findDetail("V001"))
                .doesNotThrowAnyException();
    }

    /**
     * 探索対象の絞り込みも対象にする。
     *
     * <p>読み取りの入口はクエリサービスだけではない。<strong>リクエストのたびに
     * 実行される SQL は、どの層にあっても同じ危険を持つ。</strong>
     */
    @Test
    void 探索対象の航海の絞り込みが実行できる() {
        assertThatCode(() -> voyageRepository.findConnecting(
                Location.of("JPOSA"), Location.of("USLAX")))
                .doesNotThrowAnyException();
    }

    @Test
    void 経路候補の読み取りが実行できる() {
        assertThatCode(() -> routeProposalQueryService.find(
                RoutingBookingId.of("11111111-1111-4111-8111-111111111111")))
                .doesNotThrowAnyException();
    }

    /**
     * 追跡と荷役の読み取り（IT6）。
     *
     * <p><strong>LIMIT を使う SQL を含む。</strong> 方言差が出やすい構文であり、
     * ローカル起動でだけ荷役一覧が 500 になる形は避ける。
     */
    @Test
    void 追跡と荷役の読み取りが実行できる() {
        assertThatCode(() -> trackingRepository.findByTrackingNumber(
                new com.example.cargotracker.tracking.domain.model.TrackingNumber(
                        "TRK-20260101-0001")))
                .doesNotThrowAnyException();
        assertThatCode(() -> trackingRepository.findByBookingId(
                new com.example.cargotracker.tracking.domain.model.TrackingBookingId(
                        java.util.UUID.fromString("11111111-1111-4111-8111-111111111111"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> handlingRepository.findRecent(20))
                .doesNotThrowAnyException();
        assertThatCode(() -> handlingRepository.findByBookingId(
                new com.example.cargotracker.handling.domain.model.CargoBookingId(
                        java.util.UUID.fromString("11111111-1111-4111-8111-111111111111"))))
                .doesNotThrowAnyException();
    }

    /** 追跡番号からの引き当て（US15 の入口）。 */
    @Test
    void 追跡番号からの予約の引き当てが実行できる() {
        assertThatCode(() -> cargoRepository.findByTrackingNumber("TRK-20260101-0001"))
                .doesNotThrowAnyException();
    }

    /**
     * 採番の払い出し（IT6 レビュー M5）。
     *
     * <p><strong>{@code nextval} は方言差の出る構文である。</strong> それでいて
     * 追跡番号の発行と荷主の登録という<strong>書き込みの入口</strong>で必ず通る。
     * ここが解釈できないと、ローカル起動では登録も発行もできない。
     *
     * <p>IT6 では {@link com.example.cargotracker.tracking.infrastructure.repositories
     * .TrackingSequence} が {@code infrastructure/acl} にあり、
     * <strong>検査の対象を「読み取りのクエリサービス」で数えていたために漏れた</strong>。
     * 置き場が規約から外れると、規約を前提にした検査も外れる。
     */
    @Test
    void 採番の払い出しが実行できる() {
        assertThatCode(() -> trackingSequence.next()).doesNotThrowAnyException();
        assertThatCode(() -> shipperMapper.nextSequence()).doesNotThrowAnyException();
    }
}
