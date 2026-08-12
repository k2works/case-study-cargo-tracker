package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .BillableCargoPort;
import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .ShipperDiscountPort;
import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .TrackingStatusPort;
import com.example.cargotracker.billing.domain.model.valueobjects.Adjustment;
import com.example.cargotracker.billing.domain.model.valueobjects.BillableCargo;
import com.example.cargotracker.billing.domain.model.valueobjects.BillingBookingId;
import com.example.cargotracker.billing.domain.model.valueobjects.BilledParty;
import com.example.cargotracker.billing.domain.model.valueobjects.BillingShipperId;
import com.example.cargotracker.billing.domain.model.valueobjects.CargoTypeFactor;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.FreightChargeCalculator;
import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.valueobjects.InvoiceParties;
import com.example.cargotracker.billing.domain.model.valueobjects.InvoiceType;
import com.example.cargotracker.billing.domain.model.valueobjects.Money;
import com.example.cargotracker.billing.domain.repository.InvoiceRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送料金を算出して確定するユースケース（US21 / US22）。
 *
 * <p><strong>算出と確定を分ける。</strong> 受入基準「算出結果を確認して確定操作が
 * できる」は、経理担当者が目で見て確かめる場を求めている。
 *
 * <p><strong>請求できない貨物は業務の言葉で拒む</strong>（{@link BillableCargo}）。
 * DB の一意制約に頼ると画面には 500 が出る。
 */
@Service
public class CalculateChargeCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.billing");

    private final InvoiceRepository repository;
    private final BillableCargoPort billableCargoPort;
    private final ShipperDiscountPort shipperDiscountPort;
    private final TrackingStatusPort trackingStatusPort;

    /**
     * 消費税率。
     *
     * <p><strong>設定で持つ。</strong> 税制は変わる。ただし
     * <strong>変えても発行済みの請求書は動かない</strong> — 精算書が自分の税率を
     * 保持しているためである（ADR-017 / {@code domain-model.md}）。
     */
    private final BigDecimal taxRate;

    public CalculateChargeCommandService(
            InvoiceRepository repository,
            BillableCargoPort billableCargoPort,
            ShipperDiscountPort shipperDiscountPort,
            TrackingStatusPort trackingStatusPort,
            @Value("${cargo-tracker.billing.tax-rate:0.1000}") BigDecimal taxRate) {
        this.repository = repository;
        this.billableCargoPort = billableCargoPort;
        this.shipperDiscountPort = shipperDiscountPort;
        this.trackingStatusPort = trackingStatusPort;
        this.taxRate = taxRate;
    }

    /** 結果。 */
    public enum Outcome {
        /** 算出・確定した。 */
        SUCCEEDED,
        /** 対象の貨物が見つからない。 */
        NOT_FOUND,
        /** 請求できない状態である（引取前・訂正申請中・請求済み）。 */
        REJECTED,
        /** 他の担当者が先に確定した。 */
        CONFLICTED
    }

    /**
     * 結果。
     *
     * @param outcome   結果
     * @param invoiceId 作成・更新した精算書。失敗なら {@code null}
     * @param reason    できなかった理由。<strong>そのまま画面に出す</strong>
     */
    public record Result(Outcome outcome, InvoiceId invoiceId, String reason) {
    }

    /**
     * 料金を算出して下書きの精算書を作る（US21 / US22）。
     *
     * <p><strong>確定はしない。</strong> 経理担当者が確認してから確定する。
     */
    @Transactional
    public Result calculate(String bookingId, String actor) {
        Optional<BillableCargoPort.BillableCargoSummary> found =
                billableCargoPort.findByBookingId(bookingId);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null, null);
        }
        BillableCargoPort.BillableCargoSummary cargo = found.get();

        BillingBookingId booking = new BillingBookingId(cargo.bookingId());
        BillableCargo billable = new BillableCargo(
                // **引取の判定は Tracking に聞く**（ADR-005 で 1 ビットに変換済み）
                trackingStatusPort.isClaimed(cargo.bookingId()),
                cargo.correctionRequested(),
                repository.findByBookingId(
                        booking, InvoiceType.TRANSPORT).isPresent(),
                // **経路が無ければ距離係数が 0 になり算出できない**（レビュー H3）
                cargo.distanceFactor() != null && cargo.distanceFactor().signum() > 0);
        if (!billable.isBillable()) {
            return new Result(Outcome.REJECTED, null, billable.reasonNotBillable());
        }

        Money base = FreightChargeCalculator.calculate(
                cargo.distanceFactor(), cargo.weightKg(),
                CargoTypeFactor.of(cargo.cargoType()));

        Invoice invoice = Invoice.calculate(
                new InvoiceParties(
                        repository.nextInvoiceId(), booking,
                        new BillingShipperId(cargo.shipperId(), cargo.corporate()),
                        // **宛名を凍結する**（C7）。荷主が改名しても請求書は変わらない。
                        // 表示に要る値を持てば、一覧で 1 行ずつ ACL を呼ばずに済む（C4）
                        new BilledParty(cargo.shipperName(), cargo.trackingNumber())),
                base, contractRateOf(cargo), taxRate);
        repository.save(invoice);

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("輸送料金の算出 invoiceNumber={} bookingId={} 割引率={} actor={}",
                    AuditValue.sanitize(invoice.invoiceId().value()),
                    AuditValue.sanitize(cargo.bookingId()),
                    invoice.discountRate().asPercent(),
                    AuditValue.sanitize(actor));
        }
        return new Result(Outcome.SUCCEEDED, invoice.invoiceId(), null);
    }

    /**
     * 料金調整を入力する（US21 の受入基準 6）。
     *
     * <p><strong>確定後は調整できない</strong>（集約が拒む）。
     */
    @Transactional
    public Result adjust(InvoiceId invoiceId, Adjustment adjustment, String actor) {
        Optional<Invoice> found = repository.findByInvoiceId(invoiceId);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null, null);
        }
        Invoice invoice = found.get();
        try {
            invoice.adjust(adjustment);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return new Result(Outcome.REJECTED, invoiceId, e.getMessage());
        }
        if (!repository.update(invoice)) {
            return new Result(Outcome.CONFLICTED, invoiceId, null);
        }
        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("料金調整 invoiceNumber={} 理由={} actor={}",
                    AuditValue.sanitize(invoiceId.value()),
                    AuditValue.sanitize(adjustment.reason()),
                    AuditValue.sanitize(actor));
        }
        return new Result(Outcome.SUCCEEDED, invoiceId, null);
    }

    /**
     * 料金を確定する（US21）。
     *
     * <p><strong>確定後は金額が動かない。</strong> 集約と SQL の両方が守る。
     */
    @Transactional
    public Result confirm(InvoiceId invoiceId, String actor) {
        Optional<Invoice> found = repository.findByInvoiceId(invoiceId);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null, null);
        }
        Invoice invoice = found.get();
        try {
            invoice.confirmCharge();
        } catch (IllegalStateException e) {
            return new Result(Outcome.REJECTED, invoiceId, e.getMessage());
        }
        if (!repository.update(invoice)) {
            // **黙って上書きしない。** 他の担当者が先に確定した
            return new Result(Outcome.CONFLICTED, invoiceId, null);
        }
        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("輸送料金の確定 invoiceNumber={} 請求総額={} actor={}",
                    AuditValue.sanitize(invoiceId.value()),
                    invoice.totalAmount().value(),
                    AuditValue.sanitize(actor));
        }
        return new Result(Outcome.SUCCEEDED, invoiceId, null);
    }

    /**
     * 契約割引率を引く。
     *
     * <p><strong>個人荷主には聞かない。</strong> 聞いても使わない
     * （{@code DiscountPolicy} が種別で弾く）が、無駄な問い合わせを残さない。
     */
    private DiscountRate contractRateOf(BillableCargoPort.BillableCargoSummary cargo) {
        if (!cargo.corporate()) {
            return null;
        }
        return shipperDiscountPort.findContractDiscountRate(cargo.shipperId())
                .map(DiscountRate::of)
                .orElse(null);
    }
}
