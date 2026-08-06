package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.InvalidBookingStatusTransitionException;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 予約キャンセルのユースケース（US04。遷移表 #9）。 */
@Service
public class CancelBookingCommandService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.booking");

    private final CargoRepository cargoRepository;

    public CancelBookingCommandService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    /**
     * 予約をキャンセルする。
     *
     * <p>キャンセルできるかの判断は集約が持つ。**ここで状態を見て分岐すると、
     * 遷移表の規則が 2 か所に散る。**
     */
    @Transactional
    public Outcome cancel(BookingId bookingId, String actor) {
        Optional<Cargo> found = cargoRepository.findById(bookingId);
        if (found.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        Cargo cargo = found.get();

        try {
            cargo.cancel();
        } catch (InvalidBookingStatusTransitionException e) {
            return Outcome.NOT_CANCELLABLE;
        }

        if (!cargoRepository.update(cargo)) {
            return Outcome.CONFLICTED;
        }

        AUDIT.info("貨物予約キャンセル bookingId={} actor={}",
                bookingId.value(), AuditValue.sanitize(actor));
        return Outcome.CANCELLED;
    }

    /** キャンセルの結果。 */
    public enum Outcome {
        /** キャンセルした。 */
        CANCELLED,
        /** 対象の予約が見つからない。 */
        NOT_FOUND,
        /** 現在の状態ではキャンセルできない（遷移表に無い）。 */
        NOT_CANCELLABLE,
        /** 他の操作が先行していた（楽観的ロック）。 */
        CONFLICTED
    }
}
