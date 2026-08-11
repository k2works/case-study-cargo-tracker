package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingSearchCriteria;
import com.example.cargotracker.booking.application.internal.queryservices.BookingView;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.support.CargoFixture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 追跡番号から予約を引き当てる（IT6 レビュー H9 / ふりかえり C1）。
 *
 * <p><strong>荷主から「番号を無くした」「この番号は何の貨物か」と電話が来る。</strong>
 * IT6 の時点では、営業担当者は予約を 1 件ずつ開いて追跡番号を見比べるしかなかった。
 * 予約一覧に追跡番号の列も検索欄も無かったためである。
 *
 * <p>荷役作業員向けの引き当て（{@code CargoRepository.findByTrackingNumber}）は
 * IT6 で作られていたが、<strong>それは ACL の中にあり画面からは使えなかった</strong>。
 */
@DisplayName("追跡番号から予約を引き当てる（H9）")
class BookingTrackingNumberSearchTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingQueryService queryService;

    private String 追跡番号つきの予約(String trackingNumber, String origin) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("山田物産株式会社")
                .route(origin, "USLAX")
                .status("TRACKING_ISSUED", "ROUTED")
                .trackingNumber(trackingNumber)
                .insert();
        UUID bookingId = cargo.bookingId();
        return bookingId.toString();
    }

    @Test
    void 追跡番号で予約を絞り込める() {
        String target = 追跡番号つきの予約("TRK-20260401-9001", "JPOSA");
        追跡番号つきの予約("TRK-20260401-9002", "JPOSA");

        var found = queryService.search(BookingSearchCriteria.of(null, null, null, "TRK-20260401-9001"), PageRequest.of(1));

        assertThat(found.items()).extracting(BookingView::bookingId)
                .containsExactly(target);
    }

    /**
     * <strong>末尾の数桁でも探せる。</strong> 電話で読み上げられる番号は聞き取り誤りが
     * 起きやすく、<strong>全桁が正確に伝わる前提を置けない</strong>。
     * 一致した候補が複数出ることは許す（営業担当者が荷主名で見分ける）。
     */
    @Test
    void 番号の一部でも探せる() {
        // **末尾 4 桁は他のテストと衝突しない値を使う。** 部分一致で探すため、
        // 別のテストが同じ 4 桁を使っていると、実行順によってだけ落ちる
        追跡番号つきの予約("TRK-20260401-6101", "JPYOK");
        追跡番号つきの予約("TRK-20260401-6102", "JPYOK");

        var found = queryService.search(BookingSearchCriteria.of(null, null, null, "6101"), PageRequest.of(1));

        assertThat(found.items()).extracting(BookingView::trackingNumber)
                .containsExactly("TRK-20260401-6101");
    }

    /**
     * <strong>大小文字を問わない。</strong> 電話でメモした番号を打ち込むとき、
     * 小文字で入力されるのは日常である。
     */
    @Test
    void 大小文字を問わず探せる() {
        追跡番号つきの予約("TRK-20260401-9201", "JPNGO");

        var found = queryService.search(BookingSearchCriteria.of(null, null, null, "trk-20260401-9201"), PageRequest.of(1));

        assertThat(found.items()).extracting(BookingView::trackingNumber)
                .containsExactly("TRK-20260401-9201");
    }

    /**
     * <strong>他の絞り込みと併用できる。</strong> 番号だけの検索に置き換えると、
     * 出発地で絞ってから番号で探す使い方ができなくなる。
     */
    @Test
    void 他の条件と併せて絞り込める() {
        追跡番号つきの予約("TRK-20260401-9301", "JPOSA");
        追跡番号つきの予約("TRK-20260401-9302", "JPKOB");

        var found = queryService.search(BookingSearchCriteria.of("JPKOB", null, null, "TRK-20260401-93"), PageRequest.of(1));

        assertThat(found.items()).extracting(BookingView::trackingNumber)
                .containsExactly("TRK-20260401-9302");
    }

    /** 見つからないときは 0 件を返す（例外にしない）。 */
    @Test
    void 存在しない番号では0件になる() {
        追跡番号つきの予約("TRK-20260401-9401", "JPHKT");

        var found = queryService.search(BookingSearchCriteria.of(null, null, null, "TRK-19990101-0001"), PageRequest.of(1));

        assertThat(found.items()).isEmpty();
        assertThat(found.totalItems()).isZero();
    }

    /**
     * <strong>総件数も絞り込みを反映する。</strong> 反映しないと、
     * 1 件しか出ていないのにページ送りが何ページも表示される。
     */
    @Test
    void 総件数も追跡番号の絞り込みを反映する() {
        追跡番号つきの予約("TRK-20260401-9501", "JPTYO");
        追跡番号つきの予約("TRK-20260401-9502", "JPTYO");

        var found = queryService.search(BookingSearchCriteria.of(null, null, null, "TRK-20260401-9501"), PageRequest.of(1));

        assertThat(found.totalItems()).isEqualTo(1);
    }
}
