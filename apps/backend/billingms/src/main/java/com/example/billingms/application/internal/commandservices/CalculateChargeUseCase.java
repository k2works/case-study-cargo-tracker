package com.example.billingms.application.internal.commandservices;

import com.example.billingms.application.internal.outboundservices.acl.BillableCargoSnapshot;
import com.example.billingms.application.internal.outboundservices.acl.BillingSnapshotFinder;
import com.example.billingms.domain.repository.InvoiceNumbering;
import com.example.billingms.domain.repository.InvoiceRepository;
import com.example.billingms.domain.model.valueobjects.BillingBookingId;
import com.example.billingms.domain.model.valueobjects.BillingShipperId;
import com.example.billingms.domain.model.valueobjects.CancellationFee;
import com.example.billingms.domain.model.valueobjects.CancelledAtStatus;
import com.example.billingms.domain.model.valueobjects.CargoType;
import com.example.billingms.domain.model.valueobjects.ChargeableLeg;
import com.example.billingms.domain.model.valueobjects.PortRegion;
import com.example.billingms.domain.model.valueobjects.DiscountPolicy;
import com.example.billingms.domain.model.valueobjects.DiscountRate;
import com.example.billingms.domain.model.aggregates.Invoice;
import com.example.billingms.domain.model.valueobjects.InvoiceCharges;
import com.example.billingms.domain.model.valueobjects.InvoiceLineItem;
import com.example.billingms.domain.model.valueobjects.Money;
import com.example.billingms.domain.model.valueobjects.TaxRate;
import com.example.billingms.domain.model.valueobjects.TransportCharge;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

/**
 * 料金を算出し、確定して精算書を発行する（US21・US22）。
 *
 * <p><strong>起点は経理担当者の操作である</strong>（[ADR-027] 決定 5）。
 * {@code CargoDeliveredEvent} を待たない——読む側の無い配線を先に敷かない
 * （[ADR-025] 決定 3 と同じ判断）。イベントが要るのは US23（IT12）の精算通知である。
 *
 * <p><strong>算出では何も保存しない</strong>（決定 3）。下書きを持つと、下書きのまま
 * 忘れられた精算書が溜まる。
 */
@Service
public class CalculateChargeUseCase {

    private final BillingSnapshotFinder snapshots;
    private final InvoiceRepository invoices;
    private final InvoiceNumbering numbering;
    private final Clock clock;

    public CalculateChargeUseCase(BillingSnapshotFinder snapshots, InvoiceRepository invoices,
            InvoiceNumbering numbering, Clock clock) {
        this.snapshots = snapshots;
        this.invoices = invoices;
        this.numbering = numbering;
        this.clock = clock;
    }

    /** 料金算出の対象を並べる。**経理担当者が仕事を始める場所である。** */
    public List<BillableCargoSnapshot> billable() {
        return snapshots.findAllBillable().stream()
                // **発行済みは待ち行列から消す。** 残ると、同じ貨物に 2 回請求しようとする
                .filter(snapshot -> !invoices.existsForBooking(snapshot.bookingId()))
                .toList();
    }

    /**
     * 料金を算出する。<strong>保存しない</strong>（決定 3）。
     *
     * @throws BillingNotAvailableException 料金算出の対象でないとき
     * @throws AlreadyInvoicedException すでに精算書が発行されているとき
     */
    public ChargeCalculation calculate(String bookingId) {
        BillableCargoSnapshot snapshot = requireBillable(bookingId);
        return toCalculation(snapshot);
    }

    /**
     * 料金を確定して精算書を発行する（US21-4・US21-5）。
     *
     * <p><strong>調整はここでまとめて受ける</strong>（決定 3）。算出中は保存しないため、
     * 画面が積んだ明細を確定の瞬間に渡してもらう。
     *
     * <p><strong>精算書と明細はひとまとまりで書く</strong>（IT11 レビュー 高）。
     * 明細の途中で失敗して精算書だけが残ると、調整明細を欠いた請求書が「確定済み」に
     * なる——決定 4 により金額を動かす手段が無く、{@code booking_id} は UNIQUE なので
     * 出し直しもできない。<strong>自力で復旧できない状態になる。</strong>
     */
    @Transactional
    public Invoice confirm(String bookingId, List<AdjustmentCommand> adjustments) {
        BillableCargoSnapshot snapshot = requireBillable(bookingId);
        ChargeCalculation calculation = toCalculation(snapshot);

        List<InvoiceLineItem> lineItems = (adjustments == null ? List.<AdjustmentCommand>of()
                : adjustments).stream()
                .map(adjustment -> InvoiceLineItem.of(adjustment.description(),
                        Money.yen(adjustment.amountValue())))
                .toList();

        Invoice invoice = Invoice.issue(
                new com.example.billingms.domain.model.valueobjects.InvoiceHeader(
                        numbering.next(),
                        BillingBookingId.of(snapshot.bookingId()),
                        shipperIdOf(snapshot),
                        clock.instant()),
                new InvoiceCharges(calculation.charge(), calculation.discountPolicy(),
                        calculation.cancellationFee(), calculation.taxRate()),
                lineItems,
                // 支払期限は業務の暦で決める（[ADR-028] 決定 5）
                clock.getZone());

        invoices.save(invoice);
        return invoice;
    }

    /**
     * 対象であることを確かめる。
     *
     * <p><strong>2 つを別々に断る</strong>——「対象でない」と「すでに発行済み」は、
     * 利用者にとって別の話である。同じ扱いにすると、なぜ断られたのか伝わらない。
     */
    private BillableCargoSnapshot requireBillable(String bookingId) {
        BillableCargoSnapshot snapshot = snapshots.findBillable(bookingId)
                .orElseThrow(() -> new BillingNotAvailableException(
                        "引取が終わっていない予約の料金は算出できません: " + bookingId));
        if (invoices.existsForBooking(bookingId)) {
            throw new AlreadyInvoicedException(
                    "この予約にはすでに精算書が発行されています: " + bookingId);
        }
        return snapshot;
    }

    private ChargeCalculation toCalculation(BillableCargoSnapshot snapshot) {
        TransportCharge charge = chargeOf(snapshot);

        // **未設定は 0% ではない**（[ADR-012]）。法人でも率が無ければ割引なしに倒す
        DiscountPolicy policy = snapshot.corporate() && snapshot.discountRate() != null
                ? DiscountPolicy.forCorporate(DiscountRate.of(snapshot.discountRate()))
                : DiscountPolicy.none();

        CancellationFee fee = snapshot.cancellation() == null ? null
                : CancellationFee.forStatus(
                        CancelledAtStatus.of(snapshot.cancellation().bookingStatusAtRequest()),
                        charge.baseAmount());

        // **国が異なれば輸出免税**（[ADR-027] 決定 8 の改訂）。国コードは地点マスタが
        // 持っている。どちらかが不明なら課税に倒す——免税に倒すと、国コードを
        // 引けない不具合が「消費税を取り忘れる」形で出る
        TaxRate taxRate = TaxRate.forRoute(snapshot.originCountry(),
                snapshot.destinationCountry());

        return new ChargeCalculation(snapshot.bookingId(), snapshot.shipperName(),
                snapshot.corporate(), charge, policy, snapshot.misroute(), fee, taxRate);
    }

    /**
     * 基本料金の根拠（IT11 レビュー 高 1）。
     *
     * <p><strong>旅程が無い理由を見分ける。</strong>経路が決まる前にキャンセルされた予約は
     * 旅程を持たないが、精算の一覧には並ぶ——キャンセル料 0 円として締める。
     * 一方、引取済なのに旅程が無いのは<strong>データが壊れている</strong>：0 円で通すと
     * 運んだのに請求しないことになる。
     *
     * <p>実環境で確かめたところ、この見分けが無い状態では前者が 500 を返していた
     * ——経理担当者が最初に開く画面から到達できる。
     */
    private static TransportCharge chargeOf(BillableCargoSnapshot snapshot) {
        CargoType cargoType = CargoType.of(snapshot.cargoType());
        if (snapshot.legCount() > 0) {
            // **区間の並びが無ければ断る。** 区間数だけで計算に倒すと、地域区分を
            // 入れる前の金額（すべて国内）に黙って戻る——国際輸送を安く請求し続ける
            if (snapshot.legs() == null || snapshot.legs().size() != snapshot.legCount()) {
                throw new BillingNotAvailableException(
                        "旅程の区間を取得できませんでした（地域区分が引けていません）: "
                                + snapshot.bookingId());
            }
            return TransportCharge.of(snapshot.legs().stream()
                    .map(leg -> new ChargeableLeg(PortRegion.of(leg.loadRegion()),
                            PortRegion.of(leg.unloadRegion())))
                    .toList(), snapshot.weightKg(), cargoType);
        }
        if (snapshot.cancellation() != null) {
            return TransportCharge.notTransported(snapshot.weightKg(), cargoType);
        }
        throw new BillingNotAvailableException(
                "旅程を持たない予約の料金は算出できません（運んだ記録と旅程が食い違っています）: "
                        + snapshot.bookingId());
    }

    private static BillingShipperId shipperIdOf(BillableCargoSnapshot snapshot) {
        return snapshot.corporate()
                ? BillingShipperId.corporate(snapshot.shipperId(), snapshot.shipperName())
                : BillingShipperId.individual(snapshot.shipperId(), snapshot.shipperName());
    }
}
