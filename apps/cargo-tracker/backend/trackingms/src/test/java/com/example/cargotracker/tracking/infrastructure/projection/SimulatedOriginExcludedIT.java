package com.example.cargotracker.tracking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingSummaryMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * シミュレーション由来の貨物を業務の一覧から外す（US33 §受入基準 3 /
 * [ADR-0020] 決定 4）。
 *
 * <p><b>読み口を数え上げる。</b> 印を付けただけでは混ざる。同じ表を読む口が
 * 複数あり、どれを外してどれを外さないかを 1 つずつ書く——1 本ずつ思いついた
 * 順に検査すると、次に足す読み口が黙って漏れる。</p>
 *
 * <table>
 *   <caption>trackingms で tracking_summary を読む口</caption>
 *   <tr><th>読み口</th><th>外すか</th><th>理由</th></tr>
 *   <tr><td>findAll / countAll（S40 追跡管理者）</td><td>外す</td>
 *       <td>全荷主の貨物が並ぶ。US36 の継続実行が大量に作る</td></tr>
 *   <tr><td>findAll（荷主で絞る）</td><td>外さない</td>
 *       <td>その荷主の行しか返らない。外すと確認用の利用者が辿れない（US37 §6）</td></tr>
 *   <tr><td>findByTrackingNumber / findByBooking（S41 単票）</td>
 *       <td>外さない</td><td>US34 の実行結果が工程ごとにここへ辿る</td></tr>
 *   <tr><td>countRecentlyChanged（S02 荷主）</td><td>外さない</td>
 *       <td>荷主で絞る読み。上と同じ</td></tr>
 * </table>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulatedOriginExcludedIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-15T01:00:00Z");

    @Autowired
    private ShipperOriginProjection shipperOrigins;

    @Autowired
    private TrackingProjection projection;

    @Autowired
    private TrackingSummaryMapper trackings;

    private String registerShipper(String prefix, boolean simulated) {
        String shipperId = prefix + System.nanoTime();
        shipperOrigins.on(new ShipperRegisteredEvent(shipperId, "INDIVIDUAL",
                "検査用商事", shipperId + "@example.com", "03-0000-0000",
                "東京都", null, null, simulated));
        return shipperId;
    }

    private String initializeTracking(String shipperId) {
        String trackingNumber = "T-X-" + System.nanoTime();
        projection.on(new TrackingInitializedEvent(trackingNumber,
                "b-x-" + System.nanoTime(), shipperId, "JPTYO", "USNYC", "GENERAL",
                new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-20T09:00:00Z"),
                        Instant.parse("2026-09-28T08:00:00Z"))),
                AT));
        return trackingNumber;
    }

    @Test
    @DisplayName("US33 §3: シミュレーション由来の貨物は、追跡管理者の一覧に出ない")
    void excludesSimulatedFromTheManagerWorklist() {
        String simulatedShipper = registerShipper("SHP-S-", true);
        String realShipper = registerShipper("SHP-R-", false);

        String simulatedTracking = initializeTracking(simulatedShipper);
        String realTracking = initializeTracking(realShipper);

        // **自分が作った行だけで判定する。** 共有の表で全体を数えると、別の
        // 検査が同時に書いた行で赤くなり、原因を指さない（IT16 の教訓）。
        // 件数と一覧が同じ条件かは、`countsWithTheSameConditionAsTheList` が
        // 宣言そのものを突き合わせて見る。
        assertThat(trackings.findAll(null, false, 100000))
                .extracting(TrackingSummaryMapper.TrackingSummaryRow::trackingNumber)
                .contains(realTracking)
                .doesNotContain(simulatedTracking);
    }

    /**
     * 件数と一覧が同じ条件で数える（US33 §3）。
     *
     * <p><b>件数で確かめられない。</b> 検査は同じ DB を共有して同時に走るので、
     * 「全体を数えて一覧の件数と比べる」形は別の検査が書いた行で赤くなる
     * ——原因を指さない赤は、本物の赤まで見逃させる。</p>
     *
     * <p><b>宣言を突き合わせる。</b> 見たいのは「同じ絞りを両方に書いたか」
     * なので、書いたものを読み取って比べる（書き写した条件は正典が変わっても
     * 追随しない、の同じ形）。</p>
     */
    @Test
    @DisplayName("US33 §3: 件数は一覧と同じ条件で数える（「あると言われた貨物が一覧に無い」を作らない）")
    void countsWithTheSameConditionAsTheList() throws java.io.IOException {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/example/cargotracker/tracking/infrastructure/"
                        + "persistence/TrackingSummaryMapper.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        String clause = "<if test='shipperId == null'>AND simulated = FALSE</if>";

        assertThat(source.split(java.util.regex.Pattern.quote(clause), -1).length - 1)
                .as("一覧（findAll）と件数（countAll）の両方に書く")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("US37 §6: 確認用の利用者は、自分の一覧からシミュレーションの貨物を辿れる")
    void keepsSimulatedVisibleToItsOwnShipper() {
        String simulatedShipper = registerShipper("SHP-S-", true);
        String simulatedTracking = initializeTracking(simulatedShipper);

        assertThat(trackings.findAll(simulatedShipper, false, 1000))
                .extracting(TrackingSummaryMapper.TrackingSummaryRow::trackingNumber)
                .containsExactly(simulatedTracking);
        assertThat(trackings.findByTrackingNumber(simulatedTracking))
                .as("単票も外さない——US34 の実行結果が辿る先である")
                .isNotNull();
    }

    @Test
    @DisplayName("ADR-0020 決定 4: 印が付く前に登録された荷主の貨物は本物として扱う")
    void treatsShippersWithoutTheMarkAsReal() {
        String unknownShipper = "SHP-U-" + System.nanoTime();
        String trackingNumber = initializeTracking(unknownShipper);

        assertThat(trackings.findByTrackingNumber(trackingNumber).simulated())
                .as("写しがまだ無い荷主を、業務の一覧から黙って消す側に倒さない")
                .isFalse();
    }
}
