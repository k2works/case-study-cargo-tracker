package com.example.billingms.application.internal.commandservices;

import com.example.billingms.application.port.BookingSettlementNotifier;
import com.example.billingms.application.port.InvoiceRepository;
import com.example.billingms.domain.model.Invoice;
import com.example.billingms.domain.model.Money;
import com.example.billingms.domain.model.Payment;
import com.example.billingms.domain.model.PaymentMethod;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

/**
 * 精算を処理する（US23）。
 *
 * <p><strong>入金の確認は手作業である</strong>（受入基準 23-3 の代替）。決済機関との
 * 連携先が無いため、経理担当者が通帳や入金明細を見て入れる。だからこそ
 * <strong>入れた根拠（入金日・金額・方法・参照番号）が残る</strong>ことに意味がある。
 *
 * <p><strong>入金を確認したら予約を閉じる</strong>（受入基準 23-4）。予約の状態を
 * 動かすのは bookingms であり、こちらは通知するだけである。
 *
 * <p><strong>ただしキャンセルされた予約は閉じない。</strong>精算の対象にはキャンセル済みも
 * 並ぶ（キャンセル料を締めるため）が、予約の側は引取済からしか「精算済」へ進めない
 * ——運んでいない予約に精算済は無い。
 */
@Service
public class SettleInvoiceUseCase {

    private final InvoiceRepository invoices;

    private final BookingSettlementNotifier bookings;

    private final Clock clock;

    public SettleInvoiceUseCase(InvoiceRepository invoices, BookingSettlementNotifier bookings,
            Clock clock) {
        this.invoices = invoices;
        this.bookings = bookings;
        this.clock = clock;
    }

    /**
     * 入金を確認する（受入基準 23-3・23-4）。
     *
     * <p><strong>予約への通知まで終えて初めて完了である。</strong>通知の結果を捨てると、
     * 予約が引取済のまま残っていることに誰も気づけない——例外にしないことは、
     * 記録しないことではない。
     */
    @Transactional
    public Invoice confirmPayment(String invoiceNumber, PaymentCommand command) {
        Invoice invoice = invoices.findById(invoiceNumber)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "請求書が見つかりません: " + invoiceNumber));

        Invoice confirmed = invoice.confirmPayment(Payment.of(
                Money.yen(command.amountValue()), command.paidAt(),
                PaymentMethod.of(command.method()), command.transactionReference()));

        invoices.confirmPayment(confirmed);

        // **キャンセルされた予約は「精算済」にしない**（[ADR-028] 決定 1）。
        // 予約の側は引取済からしか精算済へ進めない——知らせると相手が断り、
        // **入金の記録ごと巻き戻って、キャンセル料を永久に記録できなくなる**
        // （IT12 レビュー 高 1）。キャンセル料の請求は、請求書の側だけで閉じる
        if (!confirmed.forCancelledBooking()) {
            bookings.markSettled(confirmed.cargoBookingId().value());
        }
        return confirmed;
    }

    /**
     * 取り消す（赤伝・[ADR-028] 決定 3）。
     *
     * <p>出し直しは新しい請求番号で行う。<strong>ここでは出し直さない</strong>
     * ——金額を直してから出すのは経理担当者の判断であり、取り消しと同時に決まらない。
     */
    @Transactional
    public Invoice revoke(String invoiceNumber, String reason) {
        Invoice invoice = invoices.findById(invoiceNumber)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "請求書が見つかりません: " + invoiceNumber));

        Invoice voided = invoice.revoke(reason, clock.instant());
        invoices.revoke(voided);
        return voided;
    }

    /**
     * 支払期限を過ぎた請求書（受入基準 23-5 の代替）。
     *
     * <p><strong>未払い通知のメールは無い。</strong>経理担当者はこの一覧でしか気づけない。
     * <strong>日付は業務の暦で決める</strong>——UTC で決めると、時差の分だけ
     * 期限の判定が 1 日ずれる時間帯ができる。
     */
    public List<Invoice> overdue() {
        LocalDate today = LocalDate.now(clock);
        return invoices.findAll().stream()
                .filter(invoice -> invoice.overdue(today))
                .toList();
    }
}
