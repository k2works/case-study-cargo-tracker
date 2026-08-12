package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .BookingSettlementPort;
import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .InvoiceNotificationPort;
import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.valueobjects.Issuance;
import com.example.cargotracker.billing.domain.model.valueobjects.Money;
import com.example.cargotracker.billing.domain.model.entities.Payment;
import com.example.cargotracker.billing.domain.model.valueobjects.PaymentMethod;
import com.example.cargotracker.billing.domain.repository.InvoiceRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 精算書を発行し、入金を確認するユースケース（US23）。
 *
 * <p><strong>発行と入金確認を分ける。</strong> 発行は「請求した」、
 * 入金確認は「受け取った」であり、間に荷主の支払いがある。
 *
 * <p><strong>できないことは業務の言葉で拒む。</strong> 例外を画面まで上げると
 * 500 になり、経理担当者は何が悪いのか分からない。
 */
@Service
public class SettleInvoiceCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.billing");

    private final InvoiceRepository repository;
    private final BookingSettlementPort settlementPort;
    private final InvoiceNotificationPort notificationPort;

    /**
     * 業務の時計。
     *
     * <p><strong>期限は業務のタイムゾーンで判断する。</strong> UTC で切ると、
     * 時差の分だけ「当日」がずれ、期限当日の請求書が督促対象になる時間帯ができる。
     */
    private final Clock clock;

    /**
     * 支払期限までの日数。
     *
     * <p><strong>設定で持つが、決めた期限は請求書が保持する。</strong>
     * 都度計算すると、設定を変えた日に既発行分の期限が一斉に動く
     * （税率とまったく同じ形である）。
     */
    private final int paymentTermDays;

    public SettleInvoiceCommandService(
            InvoiceRepository repository,
            BookingSettlementPort settlementPort,
            InvoiceNotificationPort notificationPort,
            Clock clock,
            @Value("${cargo-tracker.billing.payment-term-days:30}") int paymentTermDays) {
        this.repository = repository;
        this.settlementPort = settlementPort;
        this.notificationPort = notificationPort;
        this.clock = clock;
        this.paymentTermDays = paymentTermDays;
    }

    /** 結果の種別。 */
    public enum Outcome {
        /** 成功した。 */
        DONE,
        /** 精算書が見つからない。 */
        NOT_FOUND,
        /** 業務として拒んだ（理由を画面に出す）。 */
        REJECTED,
        /** 他の担当者が先に更新した（楽観的ロック）。 */
        CONFLICTED
    }

    /**
     * 結果。
     *
     * @param outcome 種別
     * @param reason  拒んだ理由。<strong>そのまま画面に出す</strong>
     */
    public record Result(Outcome outcome, String reason) {

        static Result done() {
            return new Result(Outcome.DONE, null);
        }

        /** 成功したか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
        public boolean isDone() {
            return outcome == Outcome.DONE;
        }
    }

    /**
     * 精算書を発行する（US23 の受入基準 1・2・3）。
     *
     * <p><strong>支払期限をここで決めて請求書に持たせる。</strong>
     *
     * <p><strong>通知の記録が残せなくても発行は成立する。</strong> 予約の記録が
     * 引けない請求書はありうる（移行・削除）。発行を巻き戻すと、そういう請求書は
     * 一生発行できない。<strong>ただし残せなかったことは監査ログに残す。</strong>
     */
    @Transactional
    public Result issue(String invoiceNumber, String actor) {
        Optional<Invoice> found = repository.findByInvoiceId(InvoiceId.of(invoiceNumber));
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        Invoice invoice = found.get();

        LocalDate today = LocalDate.now(clock);
        try {
            invoice.issue(new Issuance(clock.instant(), today.plusDays(paymentTermDays)));
        } catch (IllegalStateException e) {
            return new Result(Outcome.REJECTED, e.getMessage());
        }

        if (!repository.updateSettlement(invoice)) {
            return new Result(Outcome.CONFLICTED, "他の担当者が先に更新しました");
        }

        // **記録が残せなくても発行は成立する。** 発行を巻き戻すほうが業務を止める。
        // **ただし残せなかったことは残す** — 「例外にしない」は「記録しない」を含まない。
        // 荷主から「請求書が届いていない」と言われたときに、
        // 伝えた記録が無いのか、そもそも伝えられなかったのかを区別できる
        boolean notified = notificationPort.notifyIssued(
                invoice.cargoBookingId().value(), invoice.invoiceId().value(),
                invoice.totalAmount().value(), invoice.issuance().dueDate(), actor);
        if (!notified && AUDIT.isWarnEnabled()) {
            AUDIT.warn("精算書の発行を荷主へ伝えられませんでした invoiceNumber={} bookingId={}",
                    AuditValue.sanitize(invoice.invoiceId().value()),
                    AuditValue.sanitize(invoice.cargoBookingId().value()));
        }

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("精算書の発行 invoiceNumber={} 支払期限={} actor={}",
                    AuditValue.sanitize(invoice.invoiceId().value()),
                    invoice.issuance().dueDate(),
                    AuditValue.sanitize(actor));
        }
        return Result.done();
    }

    /**
     * 支払期限を過ぎた請求書に印を付ける（US23 の受入基準 5）。
     *
     * <p><strong>画面を開いたときに判定する。</strong> 夜間バッチにすると、
     * 動いているかを誰も確かめない — 動いていないことに気づくのは
     * 督促が半月遅れたときである。
     *
     * <p><strong>期限当日は超過にしない。</strong> 「◯日まで」と伝えた当日に
     * 督促が飛ぶと、支払う側は約束を守っているのに催促を受ける。
     *
     * @return 印を付けた件数
     */
    @Transactional
    public int refreshOverdue() {
        LocalDate today = LocalDate.now(clock);
        // **超過した分だけを引く。** 未入金の全件を集約に復元すると、
        // 画面を開くたびに未入金の件数に比例した読み込みが走る（C4 と同じ形）。
        // **期限の判断そのものは集約が行う** — SQL は候補を絞るだけである
        int marked = 0;
        for (Invoice invoice : repository.findOverdueCandidates(today)) {
            if (invoice.markOverdue(today) && repository.updateSettlement(invoice)) {
                marked++;
            }
        }
        return marked;
    }

    /**
     * 入金を確認する（US23 の受入基準 4）。
     *
     * <p><strong>入金の記録と予約の精算はひと組である。</strong> 予約が精算済みに
     * ならないと、US36 の「精算済みには訂正・取り消しできない」が効かない。
     *
     * <p><strong>予約を精算済みにできなくても入金は記録する。</strong> 入金は
     * 起きた事実であり、予約の状態を理由に無かったことにはできない。
     */
    @Transactional
    public Result confirmPayment(
            String invoiceNumber, BigDecimal paidAmount, String method,
            String reference, String actor) {
        Optional<Invoice> found = repository.findByInvoiceId(InvoiceId.of(invoiceNumber));
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        Invoice invoice = found.get();

        try {
            invoice.confirmPayment(new Payment(
                    Money.yen(paidAmount == null ? BigDecimal.ZERO : paidAmount),
                    clock.instant(), PaymentMethod.of(method), reference));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return new Result(Outcome.REJECTED, e.getMessage());
        }

        if (!repository.savePayment(invoice)) {
            return new Result(Outcome.CONFLICTED, "他の担当者が先に更新しました");
        }

        // **予約を精算済みにする**（遷移表 #8）。できなくても入金は記録済みである。
        // **できなかったことを黙って捨てない。** SETTLED にならなかった予約は
        // US36 の「精算済みには訂正・取り消しできない」が効かないまま残る。
        // 気づく手段が無いと、入金済みの貨物の引取記録を後から取り消せてしまう
        boolean settled = settlementPort.settle(invoice.cargoBookingId().value());
        if (!settled && AUDIT.isWarnEnabled()) {
            AUDIT.warn("入金を確認したが予約を精算済みにできませんでした "
                            + "invoiceNumber={} bookingId={}",
                    AuditValue.sanitize(invoice.invoiceId().value()),
                    AuditValue.sanitize(invoice.cargoBookingId().value()));
        }

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("入金の確認 invoiceNumber={} 金額={} 方法={} actor={}",
                    AuditValue.sanitize(invoice.invoiceId().value()),
                    invoice.totalAmount().value(),
                    invoice.payment().method().name(),
                    AuditValue.sanitize(actor));
        }
        return settled
                ? Result.done()
                // **経理担当者に見える形で伝える。** 入金は記録できているため
                // 操作は成功だが、予約の状態が追いついていないことは知らせる
                : new Result(Outcome.DONE,
                        "入金を記録しましたが、予約を精算完了にできませんでした。"
                                + "予約の状態を確認してください");
    }
}
