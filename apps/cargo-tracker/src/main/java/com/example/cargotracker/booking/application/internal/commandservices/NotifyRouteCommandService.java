package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingView;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingNotification;
import com.example.cargotracker.booking.domain.model.NotificationContent;
import com.example.cargotracker.booking.domain.model.NotificationType;
import com.example.cargotracker.booking.domain.repository.BookingNotificationRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 確定経路を荷主に通知する（US12）。
 *
 * <p><strong>外部へは送らない</strong>（ADR-006。内部シミュレーション）。
 * 送った事実を記録することが本機能の価値であり、「送ったつもり」を後から
 * 検知できるようにするために存在する。
 */
@Service
public class NotifyRouteCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.booking");

    private final BookingQueryService queryService;
    private final NotificationContentAssembler assembler;
    private final BookingNotificationRepository repository;
    private final Clock clock;

    public NotifyRouteCommandService(
            BookingQueryService queryService,
            NotificationContentAssembler assembler,
            BookingNotificationRepository repository,
            Clock clock) {
        this.queryService = queryService;
        this.assembler = assembler;
        this.repository = repository;
        this.clock = clock;
    }

    /** 通知の結果。 */
    public enum Outcome {
        /** 送信を記録した。 */
        SENT,
        /** 予約が見つからない。 */
        NOT_FOUND,
        /** 送るべき中身が無い（経路未確定・宛先なし）。 */
        REJECTED
    }

    /**
     * 結果。
     *
     * @param outcome 結果
     * @param reason  送れなかった理由。**そのまま画面に出す**
     */
    public record Result(Outcome outcome, String reason) {

        static Result sent() {
            return new Result(Outcome.SENT, null);
        }

        static Result notFound() {
            return new Result(Outcome.NOT_FOUND, null);
        }

        static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, reason);
        }
    }

    /**
     * 通知を送って記録する。
     *
     * <p><strong>送れない予約には記録を作らない。</strong> 中身の無い通知を
     * 「送信済み」として残すと、履歴そのものが信用できなくなる。
     */
    @Transactional
    public Result notifyRoute(BookingId bookingId, String actor) {
        Optional<BookingView> found = queryService.findById(bookingId.value().toString());
        if (found.isEmpty()) {
            return Result.notFound();
        }
        BookingView booking = found.get();

        NotificationContent content;
        try {
            content = assembler.assemble(booking);
        } catch (IllegalArgumentException e) {
            // 経路が確定していない・到着予定が無い。**理由をそのまま返す**
            return Result.rejected(e.getMessage());
        }

        BookingNotification notification;
        try {
            notification = BookingNotification.succeeded(
                    bookingId, NotificationType.ROUTE_CONFIRMED,
                    booking.shipper().email(), content, clock.instant(), actor);
        } catch (IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }

        repository.save(notification);

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("経路通知 bookingId={} 宛先={} 種別={} actor={}",
                    bookingId.value(),
                    AuditValue.sanitize(notification.recipientEmail()),
                    notification.type().name(),
                    AuditValue.sanitize(actor));
        }
        return Result.sent();
    }
}
