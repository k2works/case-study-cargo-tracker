package com.example.cargotracker.billing.application;

import com.example.cargotracker.billing.domain.model.commands.CalculateInvoiceCommand;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.billing.domain.model.valueobjects.TransportRecord;
import com.example.cargotracker.billing.infrastructure.persistence.BillingCargoSnapshotMapper;
import com.example.cargotracker.billing.infrastructure.persistence.InvoiceMapper;
import com.example.cargotracker.billing.infrastructure.persistence.ShipperContractSnapshotMapper;
import com.example.cargotracker.shared.domain.location.UnLocode;
import com.example.cargotracker.shared.infrastructure.time.BusinessClockConfiguration;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 請求書を作るための材料をそろえる（US21・US23）。
 *
 * <p><b>コマンドを送らない。</b> 送るのは呼び出し側（引取の連鎖と、経理が
 * 作り直す入口）である。材料集めをここに 1 つだけ置くことで、<b>2 つの入口が
 * 別々の判定を持たない</b>——片方だけが正しくて、もう片方が誤りを素通りさせる
 * ことが起きない。</p>
 *
 * <p><b>作れない理由は 2 種類ある。</b> 待てば入るもの（購読の遅れ）と、待っても
 * 入らないもの（重量が無い・区間が無い）である。連鎖は前者を例外にして退避先へ
 * 送り、後者を要確認へ出す。手で作り直す入口は、どちらも理由として返す。</p>
 */
@Component
public class InvoiceCalculation {

    private final BillingCargoSnapshotMapper cargos;
    private final ShipperContractSnapshotMapper shippers;
    private final InvoiceMapper invoices;
    private final com.example.cargotracker.billing.infrastructure.persistence
            .BookingQuotationMapper bookingQuotations;
    private final Clock clock;

    public InvoiceCalculation(BillingCargoSnapshotMapper cargos,
            ShipperContractSnapshotMapper shippers, InvoiceMapper invoices,
            com.example.cargotracker.billing.infrastructure.persistence
                    .BookingQuotationMapper bookingQuotations, Clock clock) {
        this.cargos = cargos;
        this.shippers = shippers;
        this.invoices = invoices;
        this.bookingQuotations = bookingQuotations;
        this.clock = clock;
    }

    /** 材料をそろえた結果。 */
    public sealed interface Outcome {

        /** 送れるコマンドができた。 */
        record Ready(CalculateInvoiceCommand command) implements Outcome { }

        /** その予約にはすでに有効な請求書がある（不変条件 2 の一段目）。 */
        record AlreadyInvoiced(String invoiceId) implements Outcome { }

        /**
         * 作れない。
         *
         * @param reason 人が読む理由（要確認一覧にそのまま出す）
         * @param retryable 待てば入るか。<b>連鎖はこれで例外にするか要確認にするかを分ける</b>
         */
        record Blocked(String reason, boolean retryable) implements Outcome { }
    }

    /**
     * 追跡番号から材料をそろえる（引取の連鎖から）。
     */
    public Outcome prepare(String trackingNumber, String bookingId) {
        // **有効な請求書の確認を先に置く。** 貨物の写しが消えていても、二重に
        // 作らない判断は変わらない（少なくとも 1 回配送で同じ引取が 2 度届く）。
        var existing = invoices.findActiveByBooking(bookingId);
        if (existing != null) {
            return new Outcome.AlreadyInvoiced(existing.invoiceId());
        }
        var cargo = cargos.find(trackingNumber);
        if (cargo == null) {
            // 貨物の写しがまだ来ていない（購読の遅れ）。待てば入る。
            return new Outcome.Blocked("貨物 " + trackingNumber
                    + " の写しがまだ届いていません（請求を作れません）", true);
        }
        return prepareFor(cargo);
    }

    /**
     * 予約から材料をそろえる（経理が作り直す入口から）。
     *
     * <p>手で作り直すときに分かっているのは予約番号である。追跡番号は貨物の
     * 写しから引く——画面に打ち直させると、写し間違いが静かに別の貨物の請求に
     * なる。</p>
     */
    public Outcome prepareForBooking(String bookingId) {
        var cargo = cargos.findByBooking(bookingId);
        if (cargo == null) {
            // 手の入口では待たせない。届いていないなら、まだ作り直す時ではない。
            return new Outcome.Blocked("予約 " + bookingId
                    + " の貨物の写しが届いていません", true);
        }
        return prepareFor(cargo);
    }

    /** 見積時の概算。**結び付いていなければ {@code null}**（普通の状態）。 */
    private java.math.BigDecimal quotedAmountOf(String bookingId) {
        var quoted = bookingQuotations.findByBooking(bookingId);
        return quoted == null ? null : quoted.quotedAmount();
    }

    private Outcome prepareFor(BillingCargoSnapshotMapper.SnapshotRow cargo) {
        var existing = invoices.findActiveByBooking(cargo.bookingId());
        if (existing != null) {
            return new Outcome.AlreadyInvoiced(existing.invoiceId());
        }
        if (cargo.weightKg() == null) {
            // **待っても入らない。** 重量を運ぶ前のイベントから作られた写しなので、
            // 再試行しても同じである。
            return new Outcome.Blocked("貨物 " + cargo.trackingNumber()
                    + " の重量が分からないので請求書を作れません", false);
        }

        var contract = shippers.find(cargo.shipperId());
        if (contract == null) {
            return new Outcome.Blocked("荷主 " + cargo.shipperId()
                    + " の契約がまだ届いていません（請求を作れません）", true);
        }

        List<TransportRecord.BilledLeg> legs = new ArrayList<>();
        for (BillingCargoSnapshotMapper.LegRow leg : cargos.findLegs(cargo.trackingNumber())) {
            legs.add(new TransportRecord.BilledLeg(new UnLocode(leg.loadUnLocode()),
                    new UnLocode(leg.unloadUnLocode())));
        }
        if (legs.isEmpty()) {
            return new Outcome.Blocked("貨物 " + cargo.trackingNumber()
                    + " の区間が分からないので請求書を作れません", false);
        }

        var transport = new TransportRecord(legs, cargo.weightKg(), cargo.cargoType(),
                new UnLocode(cargo.originUnLocode()), new UnLocode(cargo.destinationUnLocode()));

        return new Outcome.Ready(new CalculateInvoiceCommand(
                nextInvoiceId(), cargo.bookingId(), cargo.shipperId(),
                contract.shipperName(), ShipperType.of(contract.shipperType()),
                DiscountRate.ofNullable(contract.discountRate()), contract.contractNumber(),
                transport,
                // 見積時の概算（注 N12）。**見積を経ない予約では null** ——
                // 「概算が無い」は欠損ではなく普通の状態で、0 円で埋めると
                // 0 円の見積があったと読まれる（S61 は概算行を出さない）。
                quotedAmountOf(cargo.bookingId()),
                // 連鎖からの算出は利用者名を持たない。**誰が作ったかは残す。**
                "system"));
    }

    /**
     * 請求書の識別子。
     *
     * <p><b>人が読める形にする。</b> 経理は請求書番号で会話するので、素の UUID
     * だと画面でも問い合わせでも扱えない。日付 + 8 桁で、{@code VARCHAR(36)} に
     * 収まる（{@code "INV-" + UUID} は 40 文字で、<b>投影が退避された</b>——
     * 集約は受け付けるので、退避先を見るまで気づけなかった）。</p>
     */
    private String nextInvoiceId() {
        return "INV-" + LocalDate.ofInstant(clock.instant(),
                        BusinessClockConfiguration.BUSINESS_ZONE)
                .format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
