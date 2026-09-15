package com.example.cargotracker.billing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * シミュレーション由来の要確認を経理の一覧（S70）から外す（US33 §受入基準 3 /
 * [ADR-0020] 決定 4）。
 *
 * <table>
 *   <caption>billingms が起票する要確認の対象</caption>
 *   <tr><th>target_type</th><th>由来を引く先</th></tr>
 *   <tr><td>INVOICE</td><td>invoice.simulated（invoice_id で引く）</td></tr>
 *   <tr><td>BOOKING</td><td>invoice.simulated（booking_id で引く）</td></tr>
 * </table>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulatedAttentionExcludedIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-15T01:00:00Z");
    private static final String ROLE = "ROLE_ATTENTION_TEST";

    @Autowired
    private ShipperContractProjection shipperContracts;

    @Autowired
    private InvoiceProjection invoiceProjection;

    @Autowired
    private BillingCargoProjection cargoSnapshots;

    @Autowired
    private AttentionItemRecorder attentionItems;

    @Autowired
    private AttentionItemMapper mapper;

    /** 請求書を 1 枚作る。返り値は請求書 ID と予約 ID。 */
    private String[] calculateInvoice(boolean simulated) {
        String shipperId = (simulated ? "SHP-S-" : "SHP-R-") + System.nanoTime();
        shipperContracts.on(new ShipperRegisteredEvent(shipperId, "INDIVIDUAL",
                "検査用商事", shipperId + "@example.com", null, null, null, null, simulated));
        String invoiceId = "INV-A-" + System.nanoTime();
        String bookingId = "B-A-" + System.nanoTime();
        invoiceProjection.on(new InvoiceCalculatedEvent(invoiceId, bookingId, shipperId,
                "検査用商事", "INDIVIDUAL", null, null,
                new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("100"),
                new BigDecimal("0.10"), false, new BigDecimal("1100"), "JPY", null,
                List.of(), "billing01", AT), "evt-" + System.nanoTime());
        return new String[] {invoiceId, bookingId};
    }

    @Test
    @DisplayName("US33 §3: シミュレーション由来の請求書あての要確認は、一覧に出ない")
    void excludesAttentionForSimulatedInvoices() {
        String[] simulated = calculateInvoice(true);
        String[] real = calculateInvoice(false);
        attentionItems.add("PROJECTION_REJECTED", "INVOICE", simulated[0], ROLE,
                "検査: シミュレーション由来", "{}", AT);
        attentionItems.add("PROJECTION_REJECTED", "INVOICE", real[0], ROLE,
                "検査: 本物", "{}", AT);

        assertThat(mapper.findOpenByRole(ROLE))
                .extracting(AttentionItemMapper.AttentionItemRow::targetId)
                .contains(real[0])
                .doesNotContain(simulated[0]);
    }

    /** 貨物の写しだけを作る（請求書は作らない）。返り値は予約 ID。 */
    private String snapshotOnly(boolean simulated) {
        String shipperId = (simulated ? "SHP-S-" : "SHP-R-") + System.nanoTime();
        shipperContracts.on(new ShipperRegisteredEvent(shipperId, "INDIVIDUAL",
                "検査用商事", shipperId + "@example.com", null, null, null, null, simulated));
        String bookingId = "B-N-" + System.nanoTime();
        cargoSnapshots.on(new TrackingInitializedEvent("T-N-" + System.nanoTime(), bookingId,
                shipperId, "JPTYO", "USNYC", "GENERAL", new BigDecimal("1200"),
                List.of(), AT), "evt-" + System.nanoTime());
        return bookingId;
    }

    @Test
    @DisplayName("US33 §3: 請求書を作れなかった予約の要確認も外す（貨物の写しから引く）")
    void excludesAttentionForSimulatedBookingsWithoutAnInvoice() {
        // **いちばん出したい要確認がここ。** 請求書から由来を引くと、請求書が
        // 無いこの場合だけ引く先を持たず、シミュレーションの失敗が経理の一覧に
        // 流れ込む——US36 の継続実行はこれを大量に作る。
        String simulated = snapshotOnly(true);
        String real = snapshotOnly(false);
        attentionItems.add("REACTION_FAILED", "BOOKING", simulated, ROLE,
                "検査: シミュレーション由来", "{}", AT);
        attentionItems.add("REACTION_FAILED", "BOOKING", real, ROLE,
                "検査: 本物", "{}", AT);

        assertThat(mapper.findOpenByRole(ROLE))
                .extracting(AttentionItemMapper.AttentionItemRow::targetId)
                .contains(real)
                .doesNotContain(simulated);
    }

    @Test
    @DisplayName("ADR-0020 決定 4: 何も写しの無い予約の要確認は本物として扱う")
    void treatsUnknownTargetsAsReal() {
        String bookingId = "B-U-" + System.nanoTime();
        attentionItems.add("REACTION_FAILED", "BOOKING", bookingId, ROLE,
                "検査: 写しが 1 つも無い予約", "{}", AT);

        assertThat(mapper.findOpenByRole(ROLE))
                .as("引く先が無いから消す、にしない。気づく手段そのものが消える")
                .extracting(AttentionItemMapper.AttentionItemRow::targetId)
                .contains(bookingId);
    }
}
