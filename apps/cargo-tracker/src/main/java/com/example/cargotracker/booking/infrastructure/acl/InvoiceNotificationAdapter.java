package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .InvoiceNotificationPort;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingView;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingNotification;
import com.example.cargotracker.booking.domain.repository.BookingNotificationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link InvoiceNotificationPort} の実装（ACL のアダプタ。US23）。
 *
 * <p><strong>通知の記録は Booking の持ち物である。</strong> 荷主の連絡先も
 * 通知の履歴も Booking が持っており、Billing から直接書くと
 * <strong>履歴の作り方が 2 通りになる</strong>（ADR-012 / ADR-015）。
 *
 * <p><strong>宛先が無ければ記録を作らない。</strong> 中身の無い通知を
 * 「伝えた」として残すと、履歴そのものが信用できなくなる
 * （{@code ResendClaimCodeCommandService} と同じ判断）。
 */
@Component
public class InvoiceNotificationAdapter implements InvoiceNotificationPort {

    private final BookingQueryService queryService;
    private final BookingNotificationRepository repository;
    private final Clock clock;

    public InvoiceNotificationAdapter(
            BookingQueryService queryService,
            BookingNotificationRepository repository,
            Clock clock) {
        this.queryService = queryService;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public boolean notifyIssued(
            String bookingId, String invoiceNumber, BigDecimal totalAmount,
            LocalDate dueDate, String actor) {
        // **形式の違う ID を例外にしない。** 発行そのものは済んでいる。
        // **例外で分岐しない** — 検査で弾く（`BookingSettlementAdapter` と同じ形）
        if (bookingId == null || bookingId.isBlank()) {
            return false;
        }
        UUID id;
        try {
            id = UUID.fromString(bookingId.strip());
        } catch (IllegalArgumentException e) {
            return false;
        }
        Optional<BookingView> found = queryService.findById(id.toString());
        if (found.isEmpty()) {
            return false;
        }
        try {
            repository.save(BookingNotification.invoiceIssued(
                    new BookingId(id), found.get().shipperEmail(),
                    invoiceNumber, totalAmount.toPlainString(), String.valueOf(dueDate),
                    clock.instant(), actor));
        } catch (IllegalArgumentException e) {
            // **中身の無い通知を残さない。** 荷主の連絡先は必須項目のため
            // 通常はここへ来ないが、**記録の作り方を通知の側で決めておく**
            return false;
        }
        return true;
    }
}
