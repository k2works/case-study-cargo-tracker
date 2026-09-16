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
     * キャンセル料の材料をそろえた結果。
     *
     * <p><b>輸送料金の {@code Outcome} とは別の型にする。</b> 1 つにまとめると、
     * どちらの経路にも「起こりえない結果」の分岐が残る——不到達の分岐は検査で
     * 覆えず、読む人には「起こりうる」ように見える。<b>種類を足したときに
     * 名乗り出る</b>性質は、家族ごとに保たれる。</p>
     */
    public sealed interface FeeOutcome {

        /** キャンセル料を積めるコマンドができた。 */
        record Ready(com.example.cargotracker.billing.domain.model.commands
                .ApplyCancellationFeeCommand command) implements FeeOutcome { }

        /** その予約にはすでに請求書がある。**調整として積むのは経理の判断**。 */
        record AlreadyInvoiced(String invoiceId) implements FeeOutcome { }

        /**
         * 算出できない。
         *
         * @param retryable 待てば入るか。<b>連鎖はこれで例外にするか要確認にするかを分ける</b>
         */
        record Blocked(String reason, boolean retryable) implements FeeOutcome { }
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

    /**
     * キャンセル料の材料をそろえる（UC22 / US30 §受入基準 9。IT15 T8）。
     *
     * <p><b>請求書がまだ無いのが通常の経路である。</b> 輸送は行われていないので
     * 輸送料金は無く、請求するのはキャンセル料だけ——キャンセル料だけの請求書に
     * なる（正典「キャンセル料の受け皿」）。</p>
     *
     * <p><b>写しが無ければ要確認へ。</b> 基本料金が出せない。<b>黙って 0 円に
     * しない</b>——取りこぼした請求はあとから取り返せない。</p>
     */
    public FeeOutcome prepareCancellationFee(String bookingId, String statusAtCancel,
            String appliedBy) {
        var existing = invoices.findActiveByBooking(bookingId);
        if (existing != null) {
            // 引取後のキャンセルは起きないので稀だが、二重に作らない判断は同じ。
            return new FeeOutcome.AlreadyInvoiced(existing.invoiceId());
        }
        var cargo = cargos.findByBooking(bookingId);
        if (cargo == null) {
            // **輸送開始前のキャンセルでは写しが無いことがある**（追跡が始まる前）。
            // 待っても入らないので、人に渡す。
            return new FeeOutcome.Blocked("予約 " + bookingId
                    + " の貨物の写しが無いのでキャンセル料を算出できません", false);
        }
        // **算出と同じ材料そろえを使う。** ガードを 1 つ足すと片方だけ直る形に
        // しない（IT15 のレビュー 中）。
        Materials gathered = gather(cargo);
        if (gathered instanceof Materials.Missing missing) {
            return new FeeOutcome.Blocked(missing.what()
                    + "のでキャンセル料を算出できません", missing.retryable());
        }
        var materials = (Materials.Ready) gathered;
        var contract = materials.contract();
        var transport = materials.transport();
        return new FeeOutcome.Ready(
                new com.example.cargotracker.billing.domain.model.commands
                        .ApplyCancellationFeeCommand(nextInvoiceId(), bookingId,
                        cargo.shipperId(), contract.shipperName(),
                        ShipperType.of(contract.shipperType()),
                        DiscountRate.ofNullable(contract.discountRate()),
                        contract.contractNumber(), transport, statusAtCancel, appliedBy));
    }

    /**
     * 請求の材料（契約と輸送実績）。<b>算出とキャンセル料で同じものを使う</b>。
     *
     * <p>そろわない理由は場面で文言が変わる（「請求書を作れません」／
     * 「キャンセル料を算出できません」）が、<b>何がそろわないか</b>と
     * <b>待てば入るか</b>は同じである。そこだけを共通にする。</p>
     */
    private sealed interface Materials {

        /** そろった。 */
        record Ready(ShipperContractSnapshotMapper.SnapshotRow contract,
                TransportRecord transport) implements Materials {
        }

        /** そろわない。{@code retryable} は待てば入るか。 */
        record Missing(String what, boolean retryable) implements Materials {
        }
    }

    /**
     * 材料をそろえる（算出とキャンセル料で共通）。
     *
     * <p><b>ガードを 1 つ足すと片方だけ直る形にしない。</b> 重量・契約・区間の
     * 3 つは、どちらの経路でも同じ順に同じ理由で要る（IT15 のレビュー 中）。</p>
     */
    private Materials gather(BillingCargoSnapshotMapper.SnapshotRow cargo) {
        if (cargo.weightKg() == null) {
            // **待っても入らない。** 重量を運ぶ前のイベントから作られた写しなので、
            // 再試行しても同じである。
            return new Materials.Missing("貨物 " + cargo.trackingNumber()
                    + " の重量が分からない", false);
        }
        var contract = shippers.find(cargo.shipperId());
        if (contract == null) {
            return new Materials.Missing("荷主 " + cargo.shipperId()
                    + " の契約がまだ届いていない", true);
        }
        List<TransportRecord.BilledLeg> legs = new ArrayList<>();
        for (BillingCargoSnapshotMapper.LegRow leg : cargos.findLegs(cargo.trackingNumber())) {
            legs.add(new TransportRecord.BilledLeg(new UnLocode(leg.loadUnLocode()),
                    new UnLocode(leg.unloadUnLocode())));
        }
        if (legs.isEmpty()) {
            return new Materials.Missing("貨物 " + cargo.trackingNumber()
                    + " の区間が分からない", false);
        }
        return new Materials.Ready(contract, new TransportRecord(legs, cargo.weightKg(),
                cargo.cargoType(), new UnLocode(cargo.originUnLocode()),
                new UnLocode(cargo.destinationUnLocode())));
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
        // **そろえるのは 1 度だけ。** 2 度呼ぶと読み口を二重に叩く。
        Materials gathered = gather(cargo);
        if (gathered instanceof Materials.Missing missing) {
            return new Outcome.Blocked(missing.what() + "ので請求書を作れません",
                    missing.retryable());
        }
        var materials = (Materials.Ready) gathered;
        var contract = materials.contract();
        var transport = materials.transport();

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
