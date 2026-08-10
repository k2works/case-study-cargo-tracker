package com.example.cargotracker.billing.infrastructure.repositories;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .BillableCargoPort;
import com.example.cargotracker.billing.application.internal.queryservices.BillingQueryService;
import com.example.cargotracker.billing.application.internal.queryservices.InvoiceView;
import com.example.cargotracker.billing.application.internal.queryservices.PendingCargoView;
import com.example.cargotracker.billing.domain.model.Adjustment;
import com.example.cargotracker.billing.domain.model.BillingBookingId;
import com.example.cargotracker.billing.domain.model.CargoTypeFactor;
import com.example.cargotracker.billing.domain.model.ChargeStatus;
import com.example.cargotracker.billing.domain.model.Invoice;
import com.example.cargotracker.billing.domain.model.InvoiceId;
import com.example.cargotracker.billing.domain.model.Percentage;
import com.example.cargotracker.billing.domain.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * {@link BillingQueryService} の実装（US21 / US22）。
 *
 * <p><strong>荷主名と追跡番号は Billing のテーブルに無い。</strong> 精算書は
 * 荷主 ID と予約 ID しか持たない（ADR-005）。表示に要る名前は
 * {@code BillableCargoPort} から受け取る — <strong>SQL で JOIN しない</strong>（ADR-015）。
 */
@Service
public class MyBatisBillingQueryService implements BillingQueryService {

    private final InvoiceRepository repository;
    private final InvoiceMapper mapper;
    private final BillableCargoPort billableCargoPort;

    public MyBatisBillingQueryService(
            InvoiceRepository repository,
            InvoiceMapper mapper,
            BillableCargoPort billableCargoPort) {
        this.repository = repository;
        this.mapper = mapper;
        this.billableCargoPort = billableCargoPort;
    }

    @Override
    public List<PendingCargoView> findPendingCargo() {
        // **すでに請求書がある貨物は出さない。** 二重請求の入口を画面に置かない
        return billableCargoPort.findPending().stream()
                .filter(cargo -> repository
                        .findByBookingId(new BillingBookingId(cargo.bookingId())).isEmpty())
                .map(MyBatisBillingQueryService::toPendingView)
                .toList();
    }

    /**
     * 表示用に変換する。
     *
     * <p><strong>貨物種別の表示名は Billing が決める</strong>（レビュー H11）。
     * ポートは素の値だけを運ぶ（ADR-005）。
     */
    private static PendingCargoView toPendingView(
            BillableCargoPort.BillableCargoSummary cargo) {
        return new PendingCargoView(
                cargo.bookingId(), cargo.trackingNumber(), cargo.shipperName(),
                cargo.corporate(), cargo.origin(), cargo.destination(),
                CargoTypeFactor.of(cargo.cargoType()).displayName(),
                cargo.weightKg(), cargo.hasException());
    }

    @Override
    public int countPendingCargo() {
        return findPendingCargo().size();
    }

    @Override
    public List<InvoiceView> findInvoices(String chargeStatus) {
        return mapper.findByChargeStatus(chargeStatus).stream()
                .map(row -> toView(row.getInvoiceNumber()))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<InvoiceView> findInvoice(String invoiceNumber) {
        return toView(invoiceNumber);
    }

    @Override
    public Optional<InvoiceView> findInvoiceByBookingId(String bookingId) {
        return repository.findByBookingId(new BillingBookingId(bookingId))
                .flatMap(invoice -> toView(invoice.invoiceId().value()));
    }

    private Optional<InvoiceView> toView(String invoiceNumber) {
        return repository.findByInvoiceId(InvoiceId.of(invoiceNumber)).map(this::toView);
    }

    private InvoiceView toView(Invoice invoice) {
        // **貨物の情報は ACL ポートで受け取る。** 精算書は荷主 ID しか持たない
        var cargo = billableCargoPort.findByBookingId(invoice.cargoBookingId().value());
        Adjustment adjustment = invoice.adjustment();
        ChargeStatus status = invoice.chargeStatus();

        return new InvoiceView(
                invoice.invoiceId().value(),
                invoice.cargoBookingId().value(),
                cargo.map(c -> c.trackingNumber()).orElse(""),
                cargo.map(c -> c.shipperName()).orElse(""),
                invoice.baseAmount().value(),
                invoice.discountRate().asPercent(),
                invoice.discountAmount().value(),
                adjustment == null ? null : adjustment.reason(),
                adjustment == null ? BigDecimal.ZERO : adjustment.reduction().value(),
                adjustment == null ? BigDecimal.ZERO : adjustment.compensation().value(),
                // **割引率と同じ変換を通す**（レビュー M6）
                Percentage.of(invoice.taxRate()),
                invoice.taxAmount().value(),
                invoice.totalAmount().value(),
                status.displayName(),
                status.badgeClass(),
                status.isConfirmed());
    }

    /** 状態の表示名（画面が列挙子名を書き写さないための対応表）。 */
    static Map<String, String> statusLabels() {
        return Map.of(
                ChargeStatus.DRAFT.name(), ChargeStatus.DRAFT.displayName(),
                ChargeStatus.CONFIRMED.name(), ChargeStatus.CONFIRMED.displayName());
    }
}
