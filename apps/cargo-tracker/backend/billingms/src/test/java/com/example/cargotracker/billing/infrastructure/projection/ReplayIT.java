package com.example.cargotracker.billing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.billing.infrastructure.persistence.BillingCargoSnapshotMapper;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceQuery;
import com.example.cargotracker.billing.infrastructure.query.InvoiceQueryHandler;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
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
 * 投影のリプレイ（[ADR-0001] コンプライアンス「投影がコマンドを送らない」）。
 *
 * <p>ArchUnit はコンパイル時の依存しか見ておらず、<b>実行時に呼ばれないことの
 * 保証ではない</b>。ここでは投影のハンドラをもう一度流し、行も区間も明細も
 * 要確認一覧も積み上がらないことを確かめる。</p>
 *
 * <p><b>要確認一覧は追記専用</b>なので、とくに積み上がりやすい（IT2 で実在した
 * 欠陥）。識別子を事実から導いているかが、ここで分かる。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReplayIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-28T01:00:00Z");

    @Autowired
    private InvoiceProjection invoiceProjection;

    @Autowired
    private BillingCargoProjection cargoProjection;

    @Autowired
    private ShipperContractProjection shipperProjection;

    @Autowired
    private InvoiceQueryHandler queries;

    @Autowired
    private BillingCargoSnapshotMapper cargos;

    @Autowired
    private AttentionItemMapper attentionItems;

    @Test
    @DisplayName("請求書と明細は、読み直しても積み上がらない")
    void replayingTheInvoiceDoesNotDuplicate() {
        String invoiceId = "INV-RP-" + System.nanoTime();
        var event = new InvoiceCalculatedEvent(invoiceId, "B-RP-" + System.nanoTime(),
                "SHP-000001", "山田商事", "INDIVIDUAL", null, BigDecimal.ZERO,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("5000"),
                new BigDecimal("55000"), "JPY",
                List.of(new InvoiceCalculatedEvent.LineItem("BASE", "基本料金（1 区間）",
                                new BigDecimal("50000"), "JPY", null),
                        new InvoiceCalculatedEvent.LineItem("TAX", "消費税",
                                new BigDecimal("5000"), "JPY", null)),
                "accountant01", AT);

        invoiceProjection.on(event, "evt-rp");
        invoiceProjection.on(event, "evt-rp");

        assertThat(queries.handle(new FindInvoiceQuery(invoiceId)).lineItems()).hasSize(2);
    }

    @Test
    @DisplayName("貨物の写しと区間は、読み直しても積み上がらない")
    void replayingTheCargoSnapshotDoesNotDuplicate() {
        String trackingNumber = "TRK-RP" + System.nanoTime() % 1000000000L;
        var event = new TrackingInitializedEvent(trackingNumber, "b-rp", "SHP-000001",
                "JPTYO", "USNYC", "GENERAL", new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                AT, AT.plusSeconds(86_400)),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                AT.plusSeconds(90_000), AT.plusSeconds(600_000))),
                AT);

        cargoProjection.on(event, "evt-rp-cargo");
        cargoProjection.on(event, "evt-rp-cargo");

        assertThat(cargos.findLegs(trackingNumber))
                .as("区間を消してから入れ直さないと、リプレイのたびに倍になる")
                .hasSize(2);
    }

    @Test
    @DisplayName("弾いた事実は、読み直しても 1 行のまま（識別子を事実から導く）")
    void replayingARejectionDoesNotPileUpAttentionItems() {
        String booking = "B-RPJ-" + System.nanoTime();
        var first = calculatedFor("INV-RPJ1-" + System.nanoTime(), booking);
        var second = calculatedFor("INV-RPJ2-" + System.nanoTime(), booking);
        invoiceProjection.on(first, "evt-rpj-1");

        invoiceProjection.on(second, "evt-rpj-2");
        invoiceProjection.on(second, "evt-rpj-2");

        assertThat(attentionItems.findOpenByRole("ROLE_ACCOUNTANT"))
                .filteredOn(item -> second.invoiceId().equals(item.targetId()))
                .as("採番していると、読み直すたびに同じ内容の行が積み上がる")
                .hasSize(1);
    }

    @Test
    @DisplayName("荷主の契約スナップショットも、読み直して増えない")
    void replayingTheShipperContractDoesNotDuplicate() {
        String shipperId = "SHP-RP-" + System.nanoTime();
        var event = new ShipperRegisteredEvent(shipperId, "CORPORATE", "山田商事",
                shipperId + "@example.com", "03-0000-0000", "東京都港区", "CT-0001", "0.1000");

        shipperProjection.on(event);
        shipperProjection.on(event);

        assertThat(attentionItems.findOpenByRole("ROLE_ACCOUNTANT"))
                .filteredOn(item -> shipperId.equals(item.targetId()))
                .isEmpty();
    }

    private static InvoiceCalculatedEvent calculatedFor(String invoiceId, String bookingId) {
        return new InvoiceCalculatedEvent(invoiceId, bookingId, "SHP-000001", "山田商事",
                "INDIVIDUAL", null, BigDecimal.ZERO, new BigDecimal("50000"), BigDecimal.ZERO,
                new BigDecimal("5000"), new BigDecimal("55000"), "JPY",
                List.of(new InvoiceCalculatedEvent.LineItem("BASE", "基本料金（1 区間）",
                        new BigDecimal("50000"), "JPY", null)),
                "accountant01", AT);
    }
}
