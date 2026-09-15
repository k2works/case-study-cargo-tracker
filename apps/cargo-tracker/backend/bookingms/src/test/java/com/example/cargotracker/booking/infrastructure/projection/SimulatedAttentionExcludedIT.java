package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * シミュレーション由来の要確認を一覧（S70）から外す（US33 §受入基準 3 /
 * [ADR-0020] 決定 4）。
 *
 * <p><b>要確認は荷主を持たない。</b> 対象の種類ごとに引く表が違うので、
 * 登録のときに解決して列に書く。<b>種類を数え上げる</b>——1 つ増えたときに
 * 黙って素通りしないよう、知らない種類は本物として扱うことも検査で固定する。</p>
 *
 * <table>
 *   <caption>bookingms が起票する要確認の対象</caption>
 *   <tr><th>target_type</th><th>由来を引く先</th></tr>
 *   <tr><td>BOOKING</td><td>cargo_summary.simulated</td></tr>
 *   <tr><td>SHIPPER</td><td>shipper.simulated</td></tr>
 * </table>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulatedAttentionExcludedIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-15T01:00:00Z");
    private static final String ROLE = "ROLE_ATTENTION_TEST";

    @Autowired
    private ShipperProjection shipperProjection;

    @Autowired
    private CargoProjection cargoProjection;

    @Autowired
    private AttentionItemRecorder attentionItems;

    @Autowired
    private AttentionItemMapper mapper;

    private String registerShipper(boolean simulated) {
        String shipperId = (simulated ? "SHP-S-" : "SHP-R-") + System.nanoTime();
        shipperProjection.on(new ShipperRegisteredEvent(shipperId, "INDIVIDUAL",
                "検査用商事", shipperId + "@example.com", null, null, null, null, simulated));
        return shipperId;
    }

    private String bookCargo(String shipperId) {
        String bookingId = "B-A-" + System.nanoTime();
        cargoProjection.on(new CargoBookedEvent(bookingId, shipperId, "JPTYO", "USNYC",
                LocalDate.of(2026, Month.DECEMBER, 1), "GENERAL", new BigDecimal("1200"),
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"),
                10, "検査用の貨物", null, null, null, null, "sales01"));
        return bookingId;
    }

    @Test
    @DisplayName("US33 §3: シミュレーションが作った予約の要確認は、一覧に出ない")
    void excludesAttentionForSimulatedBookings() {
        String simulatedBooking = bookCargo(registerShipper(true));
        String realBooking = bookCargo(registerShipper(false));
        attentionItems.add("PROJECTION_REJECTED", "BOOKING", simulatedBooking, ROLE,
                "検査: シミュレーション由来", "{}", AT);
        attentionItems.add("PROJECTION_REJECTED", "BOOKING", realBooking, ROLE,
                "検査: 本物", "{}", AT);

        assertThat(mapper.findOpenByRole(ROLE))
                .extracting(AttentionItemMapper.AttentionItemRow::targetId)
                .contains(realBooking)
                .doesNotContain(simulatedBooking);
    }

    @Test
    @DisplayName("US33 §3 / N12: 荷主あての要確認も外す（対象は予約だけではない）")
    void excludesAttentionForSimulatedShippers() {
        String simulatedShipper = registerShipper(true);
        String realShipper = registerShipper(false);
        attentionItems.add("PROJECTION_REJECTED", "SHIPPER", simulatedShipper, ROLE,
                "検査: シミュレーション由来の荷主", "{}", AT);
        attentionItems.add("PROJECTION_REJECTED", "SHIPPER", realShipper, ROLE,
                "検査: 本物の荷主", "{}", AT);

        assertThat(mapper.findOpenByRole(ROLE))
                .extracting(AttentionItemMapper.AttentionItemRow::targetId)
                .contains(realShipper)
                .doesNotContain(simulatedShipper);
    }

    @Test
    @DisplayName("ADR-0020 決定 4: 知らない対象の種類は本物として扱う")
    void treatsUnknownTargetTypesAsReal() {
        String targetId = "X-" + System.nanoTime();
        attentionItems.add("PROJECTION_REJECTED", "VOYAGE", targetId, ROLE,
                "検査: 共有の設備には印が付かない", "{}", AT);

        assertThat(mapper.findOpenByRole(ROLE))
                .as("引く先が無い種類を黙って消すと、気づく手段そのものが消える")
                .extracting(AttentionItemMapper.AttentionItemRow::targetId)
                .contains(targetId);
    }
}
