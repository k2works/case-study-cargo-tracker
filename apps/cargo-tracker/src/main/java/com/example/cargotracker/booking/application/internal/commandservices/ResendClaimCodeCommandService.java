package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingView;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingNotification;
import com.example.cargotracker.booking.domain.repository.BookingNotificationRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 引取確認コードを荷主に再度伝える（US35 / IT12 持ち越し C7）。
 *
 * <p>荷受人がコードを忘れて港に来ることは起きる。IT12 は画面に
 * 「担当営業へお問い合わせください」と案内を足したが、
 * <strong>問い合わせを受けた営業に伝える手段が無かった</strong>。
 * 案内した先が行き止まりになっていた。
 *
 * <p><strong>再発行はしない。</strong> 発行し直すと、元のコードを持って港に来た
 * 荷受人が弾かれる。<strong>忘れた人を助ける操作が、覚えていた人を締め出す。</strong>
 * ここで伝えるのは<strong>いま有効なコードそのもの</strong>である。
 *
 * <p><strong>外部へは送らない</strong>（ADR-006）。残すのは「いつ・誰が伝えたか」であり、
 * 「伝えたつもり」を後から検知できるようにすることが目的である。
 */
@Service
public class ResendClaimCodeCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.booking");

    private final BookingQueryService queryService;
    private final BookingNotificationRepository repository;
    private final Clock clock;

    public ResendClaimCodeCommandService(
            BookingQueryService queryService,
            BookingNotificationRepository repository,
            Clock clock) {
        this.queryService = queryService;
        this.repository = repository;
        this.clock = clock;
    }

    /** 結果。 */
    public enum Outcome {
        /** 伝えた事実を記録した。 */
        SENT,
        /** 予約が見つからない。 */
        NOT_FOUND,
        /** 伝えるべき中身が無い（コードが未採番）。 */
        REJECTED
    }

    /**
     * 結果。
     *
     * @param outcome 結果
     * @param reason  伝えられなかった理由。<strong>そのまま画面に出す</strong>
     */
    public record Result(Outcome outcome, String reason) {
    }

    /**
     * コードを再度伝えて記録する。
     *
     * <p><strong>コードが無い予約には記録を作らない。</strong> 中身の無い通知を
     * 「伝えた」として残すと、履歴そのものが信用できなくなる。
     */
    @Transactional
    public Result resend(BookingId bookingId, String actor) {
        Optional<BookingView> found = queryService.findById(bookingId.value().toString());
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        BookingView booking = found.get();

        BookingNotification notification;
        try {
            notification = BookingNotification.claimCodeResent(
                    bookingId, booking.shipperEmail(), booking.claimCode(),
                    clock.instant(), actor);
        } catch (IllegalArgumentException e) {
            return new Result(Outcome.REJECTED, e.getMessage());
        }

        repository.save(notification);

        if (AUDIT.isInfoEnabled()) {
            // **コードそのものはログに出さない。** 受け取ってよい人かを確かめる秘密の値である
            AUDIT.info("引取確認コードの再伝達 bookingId={} 宛先={} actor={}",
                    bookingId.value(),
                    AuditValue.sanitize(notification.recipientEmail()),
                    AuditValue.sanitize(actor));
        }
        return new Result(Outcome.SENT, null);
    }
}
