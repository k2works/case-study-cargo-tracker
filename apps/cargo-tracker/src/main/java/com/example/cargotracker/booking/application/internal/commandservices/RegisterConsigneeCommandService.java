package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.Consignee;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 予約に荷受人を登録する（US16）。
 *
 * <p>荷受人は<strong>引取時の本人確認に用いる</strong>のが最初の使い道であり、
 * それが US04（予約登録）ではなく本ストーリーで扱う理由である。
 * 国際輸送では荷受人が後から決まることがあり、予約の時点では未確定でありうる。
 */
@Service
public class RegisterConsigneeCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.booking");

    private final CargoRepository cargoRepository;

    public RegisterConsigneeCommandService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    /**
     * 荷受人を登録する（訂正も同じ操作である）。
     *
     * <p><strong>「登録」と「訂正」を別の操作にしない。</strong> 利用者にとっては
     * どちらも「荷受人を入れる」であり、分けると画面に 2 つのボタンが並ぶ。
     */
    @Transactional
    public Result register(BookingId bookingId, Consignee consignee, String actor) {
        Optional<Cargo> found = cargoRepository.findById(bookingId);
        if (found.isEmpty()) {
            return Result.NOT_FOUND;
        }
        Cargo cargo = found.get();

        try {
            cargo.registerConsignee(consignee);
        } catch (IllegalStateException e) {
            // 引き渡し済み以降。**書き換えると誰に渡したかの記録が作り変えられる**
            return Result.REJECTED;
        }
        cargoRepository.updateConsignee(cargo);

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("荷受人登録 bookingId={} consignee={} actor={}",
                    bookingId.value(),
                    AuditValue.sanitize(consignee.name()),
                    AuditValue.sanitize(actor));
        }
        return Result.REGISTERED;
    }

    /** 登録の結果。 */
    public enum Result {
        /** 登録した。 */
        REGISTERED,
        /** 予約が見つからない。 */
        NOT_FOUND,
        /** 引き渡し済み以降のため変更できない。 */
        REJECTED
    }
}
