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

/**
 * 予約情報を経路設計者に引き渡すユースケース（US06。遷移表 #2）。
 *
 * <p><strong>通知は送らない。</strong> ADR-006 により外部連携は実装しない。
 * 引き渡した予約が経路設計者の作業入口（経路割り当て待ち一覧）に現れることが、
 * 業務上の「引き渡し」である（US06 の受入基準）。
 */
@Service
public class AssignToRoutingCommandService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.booking");

    private final CargoRepository cargoRepository;

    public AssignToRoutingCommandService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    /**
     * 予約を経路設計者に引き渡す。
     *
     * <p>引き渡せるかの判断は集約が持つ。**ここで状態を見て分岐すると、
     * 遷移表の規則が 2 か所に散る。**
     */
    @Transactional
    public Outcome assign(BookingId bookingId, String actor) {
        Optional<Cargo> found = cargoRepository.findById(bookingId);
        if (found.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        Cargo cargo = found.get();

        try {
            cargo.assignToRouting();
        } catch (InvalidBookingStatusTransitionException e) {
            return Outcome.NOT_ASSIGNABLE;
        }

        if (!cargoRepository.update(cargo)) {
            return Outcome.CONFLICTED;
        }

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("経路設計者への引き渡し bookingId={} actor={}",
                    bookingId.value(), AuditValue.sanitize(actor));
        }
        return Outcome.ASSIGNED;
    }

    /** 引き渡しの結果。 */
    public enum Outcome {
        /** 引き渡した。 */
        ASSIGNED,
        /** 対象の予約が見つからない。 */
        NOT_FOUND,
        /** 現在の状態では引き渡せない（引き渡し済みを含む）。 */
        NOT_ASSIGNABLE,
        /** 他の操作が先行していた（楽観的ロック）。 */
        CONFLICTED
    }
}
