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
import java.util.Set;
import java.util.UUID;
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

    /**
     * 業務の時計（C1）。
     *
     * <p><strong>引取日を UTC で切らない。</strong> 日本時間の朝に済んだ引取が
     * 前日扱いになり、月初・月末の締めがずれる。
     */
    private final java.time.Clock clock;

    /** 督促の記録（IT14 レビュー C3）。 */
    private final com.example.cargotracker.billing.domain.repository.ReminderRepository
            reminders;

    public MyBatisBillingQueryService(
            InvoiceRepository repository,
            InvoiceMapper mapper,
            BillableCargoPort billableCargoPort,
            java.time.Clock clock,
            com.example.cargotracker.billing.domain.repository.ReminderRepository reminders) {
        this.repository = repository;
        this.mapper = mapper;
        this.billableCargoPort = billableCargoPort;
        this.clock = clock;
        this.reminders = reminders;
    }

    @Override
    public List<PendingCargoView> findPendingCargo() {
        // **請求済みの予約をまとめて引く**（IT13 レビュー C4）。
        // 1 件ずつ「請求書があるか」を聞くと、行数に比例して問い合わせが増える
        Set<String> invoiced = mapper.findInvoicedBookingIds().stream()
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.toSet());

        // **すでに請求書がある貨物は出さない。** 二重請求の入口を画面に置かない
        return billableCargoPort.findPending().stream()
                .filter(cargo -> !invoiced.contains(cargo.bookingId()))
                .map(this::toPendingView)
                .toList();
    }

    /**
     * 表示用に変換する。
     *
     * <p><strong>貨物種別の表示名は Billing が決める</strong>（レビュー H11）。
     * ポートは素の値だけを運ぶ（ADR-005）。
     */
    private PendingCargoView toPendingView(
            BillableCargoPort.BillableCargoSummary cargo) {
        return new PendingCargoView(
                cargo.bookingId(), cargo.trackingNumber(), cargo.shipperName(),
                cargo.corporate(), cargo.origin(), cargo.destination(),
                CargoTypeFactor.of(cargo.cargoType()).displayName(),
                cargo.weightKg(), cargo.hasException(),
                // **業務タイムゾーンの日付にする**（C1）
                cargo.claimedAt() == null
                        ? null
                        : java.time.LocalDate.ofInstant(cargo.claimedAt(), clock.getZone()));
    }

    @Override
    public int countPendingCargo() {
        // **一覧を組み立てずに数える**（C4）。ダッシュボードは表示のたびにこれを呼ぶ。
        // 件数だけが要るのに一覧を作ると、トップページが件数に比例して重くなる
        return findPendingCargo().size();
    }

    @Override
    public int countOverdueInvoices() {
        return mapper.countByPaymentStatus(
                com.example.cargotracker.billing.domain.model.PaymentStatus.OVERDUE.name());
    }

    @Override
    public List<InvoiceView> findInvoices(
            com.example.cargotracker.billing.application.internal.queryservices
                    .InvoiceSearchCriteria criteria) {
        // **行をまるごと受け取って組み立てる**（C4）。番号だけを取って 1 件ずつ
        // 引き直すと、行数に比例して問い合わせが増える
        return mapper.search(criteria, startOf(criteria.issuedFrom()),
                        startOf(criteria.issuedTo() == null
                                ? null : criteria.issuedTo().plusDays(1))).stream()
                .map(MyBatisInvoiceRepository::toDomain)
                .map(this::toView)
                .toList();
    }

    @Override
    public java.util.List<com.example.cargotracker.billing.application.internal.queryservices
            .ReminderView> findReminders(String invoiceNumber) {
        return reminders.findByInvoiceId(
                        com.example.cargotracker.billing.domain.model.InvoiceId
                                .of(invoiceNumber)).stream()
                .map(r -> new com.example.cargotracker.billing.application.internal
                        .queryservices.ReminderView(
                        r.remindedAt(), r.remindedBy(), r.note()))
                .toList();
    }

    @Override
    public int countAwaitingIssue() {
        return mapper.countAwaitingIssue();
    }

    /**
     * 業務のタイムゾーンでのその日の始まり。
     *
     * <p><strong>DB のタイムゾーンで日付に丸めない</strong>（CI は UTC で動く）。
     * 丸めると、時差の分だけ月初・月末の請求書が隣の月に落ちる。
     */
    private java.time.Instant startOf(java.time.LocalDate date) {
        return date == null ? null : date.atStartOfDay(clock.getZone()).toInstant();
    }

    @Override
    public Optional<InvoiceView> findInvoice(String invoiceNumber) {
        return toView(invoiceNumber);
    }

    @Override
    public Optional<InvoiceView> findInvoiceByBookingId(String bookingId) {
        return repository.findByBookingId(
                        new BillingBookingId(bookingId),
                        com.example.cargotracker.billing.domain.model.InvoiceType.TRANSPORT)
                .flatMap(invoice -> toView(invoice.invoiceId().value()));
    }

    private Optional<InvoiceView> toView(String invoiceNumber) {
        return repository.findByInvoiceId(InvoiceId.of(invoiceNumber)).map(this::toView);
    }

    private InvoiceView toView(Invoice invoice) {
        // **宛名と追跡番号は請求書が持つ**（C7）。ACL ポートを呼ばない。
        // 荷主が改名しても発行済みの請求書は変わらず、
        // **一覧を描くのに 1 行ずつ問い合わせる必要も無い**（C4）
        Adjustment adjustment = invoice.adjustment();
        ChargeStatus status = invoice.chargeStatus();

        return new InvoiceView(
                invoice.invoiceId().value(),
                invoice.cargoBookingId().value(),
                invoice.parties().billed().trackingNumber(),
                invoice.parties().billed().shipperName(),
                invoice.shipperId().value(),
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
                status.isConfirmed(),
                // **精算（US23）。** 未発行は日付を持たない
                invoice.isIssued()
                        ? java.time.LocalDate.ofInstant(
                                invoice.issuance().issuedAt(), clock.getZone())
                        : null,
                invoice.isIssued() ? invoice.issuance().dueDate() : null,
                invoice.paymentStatus() == null ? null : invoice.paymentStatus().displayName(),
                invoice.paymentStatus() == null ? null : invoice.paymentStatus().badgeClass(),
                invoice.isIssued(),
                invoice.paymentStatus() != null && invoice.paymentStatus().isPaid(),
                // **入金の中身を画面に出す**（帳簿との照合に要る）。未入金なら無い
                paymentDetail(invoice),
                // **何日遅れているかは集約が数える**（画面が引き算を書き直さない）
                invoice.isIssued()
                        ? invoice.issuance().daysOverdue(java.time.LocalDate.now(clock)) : 0L,
                // **法人かどうかは割引率から逆算しない**（C6）
                invoice.corporate());
    }

    /** 入金の記録を表示用に変換する（<strong>未入金なら {@code null}</strong>）。 */
    private InvoiceView.PaymentDetail paymentDetail(Invoice invoice) {
        com.example.cargotracker.billing.domain.model.Payment paid = invoice.payment();
        if (paid == null) {
            return null;
        }
        return new InvoiceView.PaymentDetail(
                paid.paidAmount().value(),
                java.time.LocalDateTime.ofInstant(paid.paidAt(), clock.getZone()),
                paid.method().displayName(),
                paid.transactionReference());
    }

    /** 状態の表示名（画面が列挙子名を書き写さないための対応表）。 */
    static Map<String, String> statusLabels() {
        return Map.of(
                ChargeStatus.DRAFT.name(), ChargeStatus.DRAFT.displayName(),
                ChargeStatus.CONFIRMED.name(), ChargeStatus.CONFIRMED.displayName());
    }
}
