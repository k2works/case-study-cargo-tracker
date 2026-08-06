package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageQueryService;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
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

    @Test
    void 荷主の検索が実行できる() {
        assertThatCode(() -> shipperQueryService.search("山田", PageRequest.of(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> shipperQueryService.search(null, PageRequest.of(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> shipperQueryService.findById("11111111-1111-4111-8111-111111111111"))
                .doesNotThrowAnyException();
    }

    @Test
    void 貨物予約の検索が実行できる() {
        assertThatCode(() -> bookingQueryService.search(
                "JPOSA", "USLAX", "PRELIMINARY", PageRequest.of(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> bookingQueryService.search(null, null, null, PageRequest.of(1)))
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
}
