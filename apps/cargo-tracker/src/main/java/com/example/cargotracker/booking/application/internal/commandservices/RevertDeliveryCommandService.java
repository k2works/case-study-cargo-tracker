package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 引き渡しの完了を取り消す（US36）。
 *
 * <p><strong>承認された取り消しだけがここへ来る。</strong> 手で戻せる形にすると、
 * 引き渡しの証明（US35）が現場の判断で消せることになる。
 */
@Service
public class RevertDeliveryCommandService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.booking");

    private final CargoRepository cargoRepository;

    public RevertDeliveryCommandService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    /**
     * 配送完了を輸送中へ戻す。
     *
     * <p><strong>新しいトランザクションで動く</strong>（ADR-009）。
     *
     * @return 戻せたか。<strong>精算済みは戻せない</strong>（返金を伴う別業務）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean revert(UUID bookingId, String approvedBy) {
        Optional<Cargo> found = cargoRepository.findById(new BookingId(bookingId));
        if (found.isEmpty()) {
            return false;
        }
        Cargo cargo = found.get();
        if (!cargo.canRevertDelivery()) {
            return false;
        }
        cargo.revertDelivery();
        if (!cargoRepository.update(cargo)) {
            return false;
        }
        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("引き渡しの取り消し bookingId={} actor={}",
                    bookingId, AuditValue.sanitize(approvedBy));
        }
        return true;
    }
}
