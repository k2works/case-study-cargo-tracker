package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .BillableCargoPort;
import com.example.cargotracker.billing.domain.model.BilledParty;
import com.example.cargotracker.billing.domain.model.BillingBookingId;
import com.example.cargotracker.billing.domain.model.BillingShipperId;
import com.example.cargotracker.billing.domain.model.CargoTypeFactor;
import com.example.cargotracker.billing.domain.model.FreightChargeCalculator;
import com.example.cargotracker.billing.domain.model.Invoice;
import com.example.cargotracker.billing.domain.model.InvoiceId;
import com.example.cargotracker.billing.domain.model.InvoiceParties;
import com.example.cargotracker.billing.domain.model.InvoiceType;
import com.example.cargotracker.billing.domain.model.Money;
import com.example.cargotracker.billing.domain.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * キャンセル料の請求書を作るユースケース（US30。ADR-020）。
 *
 * <p><strong>金額を決めるのは Billing である。</strong> Booking が運んでくるのは
 * 「キャンセルされた」という事実と<strong>料率</strong>だけであり、
 * 基準額（輸送料金の基本料金）はここで算出する。
 * <strong>Booking から金額を送ると基準額が 2 つ生まれる。</strong>
 *
 * <p><strong>下書きで作る。</strong> 承認と同時に確定すると、経理担当者が
 * 金額を目で見る場が無くなる（US21 と同じ判断）。
 */
@Service
public class ChargeCancellationFeeCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.billing");

    private final InvoiceRepository repository;
    private final BillableCargoPort billableCargoPort;

    /**
     * 消費税率。
     *
     * <p><strong>設定で持つ。</strong> ただし変えても発行済みの請求書は動かない
     * （精算書が自分の税率を保持している）。
     */
    private final BigDecimal taxRate;

    public ChargeCancellationFeeCommandService(
            InvoiceRepository repository,
            BillableCargoPort billableCargoPort,
            @Value("${cargo-tracker.billing.tax-rate:0.1000}") BigDecimal taxRate) {
        this.repository = repository;
        this.billableCargoPort = billableCargoPort;
        this.taxRate = taxRate;
    }

    /** 結果。 */
    public enum Outcome {
        /** キャンセル料の請求書を作った。 */
        CHARGED,
        /** キャンセル料が発生しない（<strong>料率 0</strong>）。 */
        FREE,
        /** すでにキャンセル料の請求書がある。 */
        ALREADY_CHARGED,
        /** 対象の貨物が見つからない、または金額を算出できない。 */
        NOT_CHARGEABLE
    }

    /**
     * キャンセル料を算出して請求書を作る。
     *
     * <p><strong>料率 0 では作らない。</strong> 0 円の請求書は送る相手がいない。
     * 受入基準も「キャンセル料が<strong>発生する場合</strong>」と書いている。
     *
     * <p><strong>二度作らない。</strong> イベントは再送されうる（ADR-009）。
     * 同じ予約に 2 通のキャンセル料の請求書が並ぶと、荷主はどちらが本物か分からない
     * （DB の一意制約でも防いでいるが、制約に頼ると例外がログを埋める）。
     *
     * @param bookingId 予約 ID
     * @param feeRate   キャンセル料の料率（<strong>申請時点</strong>）
     */
    @Transactional
    public Outcome charge(String bookingId, BigDecimal feeRate) {
        if (feeRate == null || feeRate.signum() <= 0) {
            return Outcome.FREE;
        }
        BillingBookingId booking = new BillingBookingId(bookingId);
        if (repository.findByBookingId(booking, InvoiceType.CANCELLATION).isPresent()) {
            return Outcome.ALREADY_CHARGED;
        }

        Optional<BillableCargoPort.BillableCargoSummary> found =
                billableCargoPort.findByBookingId(bookingId);
        if (found.isEmpty()) {
            return Outcome.NOT_CHARGEABLE;
        }
        BillableCargoPort.BillableCargoSummary cargo = found.get();
        // **経路が無ければ距離係数が 0 になり算出できない**（レビュー H3 と同じ形）。
        // 経路を押さえる前のキャンセルは料率も 0 であり、ここへは到達しない
        if (cargo.distanceFactor() == null || cargo.distanceFactor().signum() <= 0) {
            return Outcome.NOT_CHARGEABLE;
        }

        Money base = FreightChargeCalculator.calculate(
                cargo.distanceFactor(), cargo.weightKg(),
                CargoTypeFactor.of(cargo.cargoType()));

        Invoice invoice = Invoice.cancellationFee(
                new InvoiceParties(
                        repository.nextInvoiceId(),
                        booking,
                        new BillingShipperId(cargo.shipperId(), cargo.corporate()),
                        // **宛名と追跡番号を凍結する**（C7）。荷主が改名しても変わらない
                        new BilledParty(cargo.shipperName(), cargo.trackingNumber())),
                base, feeRate, taxRate);
        repository.save(invoice);

        AUDIT.info("キャンセル料を算出しました invoiceNumber={} bookingId={} feeRate={}",
                invoice.invoiceId().value(), bookingId, feeRate);
        return Outcome.CHARGED;
    }

    /** 作成したキャンセル料の請求書（<strong>無ければ空</strong>）。 */
    public Optional<InvoiceId> findCancellationInvoice(String bookingId) {
        return repository.findByBookingId(
                        new BillingBookingId(bookingId), InvoiceType.CANCELLATION)
                .map(Invoice::invoiceId);
    }
}
