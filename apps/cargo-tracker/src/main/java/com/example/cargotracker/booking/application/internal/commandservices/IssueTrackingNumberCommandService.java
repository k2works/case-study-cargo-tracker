package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.TrackingPort;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingTrackingNumber;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.InvalidBookingStatusTransitionException;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追跡番号を発行するユースケース（US14。遷移表 #5）。
 *
 * <p><strong>採番と追跡の開始は Tracking の仕事である。</strong> ここでするのは
 * 発行を依頼し、返ってきた番号を予約に記録することだけである（ADR-005）。
 *
 * <p><strong>1 つのトランザクションで書く。</strong> 追跡レコードだけが作られて
 * 予約に番号が付かないと、荷主に伝える番号が無いまま追跡だけが存在する。
 *
 * <p><strong>メール通知は送らない。</strong> ADR-006 により外部連携は実装しない。
 * 発行した番号は予約詳細に表示する（US14 の受入基準の代替）。
 */
@Service
public class IssueTrackingNumberCommandService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.booking");

    private final CargoRepository cargoRepository;
    private final TrackingPort trackingPort;

    public IssueTrackingNumberCommandService(
            CargoRepository cargoRepository, TrackingPort trackingPort) {
        this.cargoRepository = cargoRepository;
        this.trackingPort = trackingPort;
    }

    /** 追跡番号を発行する。 */
    @Transactional
    public Result issue(BookingId bookingId, String actor) {
        Optional<Cargo> found = cargoRepository.findById(bookingId);
        if (found.isEmpty()) {
            return Result.notFound();
        }
        Cargo cargo = found.get();

        // **発行できる状態かを先に確かめる。** 確かめずに採番すると、
        // 拒否された場合に番号だけが飛ぶ（シーケンスは戻らない）
        if (!cargo.canIssueTrackingNumber()) {
            return Result.rejected("この状態の予約には追跡番号を発行できません");
        }

        String issued = trackingPort.issue(bookingId.value());
        try {
            cargo.issueTrackingNumber(new BookingTrackingNumber(issued));
        } catch (InvalidBookingStatusTransitionException e) {
            return Result.rejected("この状態の予約には追跡番号を発行できません");
        }

        if (!cargoRepository.updateTrackingNumber(cargo)) {
            return Result.conflicted();
        }

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("追跡番号発行 bookingId={} trackingNumber={} actor={}",
                    bookingId.value(), issued, AuditValue.sanitize(actor));
        }
        return Result.issued(issued);
    }

    /** 発行の結果。 */
    public enum Outcome {
        /** 発行した。 */
        ISSUED,
        /** 対象の予約が見つからない。 */
        NOT_FOUND,
        /** 発行できない状態である。 */
        REJECTED,
        /** 他の操作が先行していた（楽観的ロック）。 */
        CONFLICTED
    }

    /**
     * 発行の結果。
     *
     * @param outcome        結果の種別
     * @param trackingNumber 発行した追跡番号。失敗時は {@code null}
     * @param reason         発行できなかった理由。成功時は {@code null}
     */
    public record Result(Outcome outcome, String trackingNumber, String reason) {

        static Result issued(String trackingNumber) {
            return new Result(Outcome.ISSUED, trackingNumber, null);
        }

        static Result notFound() {
            return new Result(Outcome.NOT_FOUND, null, null);
        }

        static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, null, reason);
        }

        static Result conflicted() {
            return new Result(Outcome.CONFLICTED, null, null);
        }

        public boolean isIssued() {
            return outcome == Outcome.ISSUED;
        }
    }
}
