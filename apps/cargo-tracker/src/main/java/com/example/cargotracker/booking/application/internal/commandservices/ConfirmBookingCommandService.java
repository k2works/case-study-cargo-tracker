package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.VoyageCapacityPort;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.InvalidBookingStatusTransitionException;
import com.example.cargotracker.booking.domain.model.Leg;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 予約を確定するユースケース（US13。遷移表 #4）。
 *
 * <p><strong>確定の瞬間に空き容量を再判定する</strong>（IT5 レビュー M3）。候補の算出時に
 * 判定した値をそのまま信じると、算出から確定までの間に他の貨物が同じ便に
 * 割り当てられていた場合、<strong>満船の便を確定できてしまう</strong>。
 *
 * <p><strong>通知は送らない。</strong> ADR-006 により外部連携は実装しない。確定した予約が
 * 追跡管理者の作業入口（追跡番号発行待ち一覧）に現れることが、業務上の
 * 「発行依頼」である（US13 の受入基準）。
 */
@Service
public class ConfirmBookingCommandService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.booking");

    private final CargoRepository cargoRepository;
    private final VoyageCapacityPort voyageCapacity;

    public ConfirmBookingCommandService(
            CargoRepository cargoRepository, VoyageCapacityPort voyageCapacity) {
        this.cargoRepository = cargoRepository;
        this.voyageCapacity = voyageCapacity;
    }

    /** 予約を確定する。 */
    @Transactional
    public Result confirm(BookingId bookingId, String actor) {
        Optional<Cargo> found = cargoRepository.findById(bookingId);
        if (found.isEmpty()) {
            return Result.notFound();
        }
        Cargo cargo = found.get();

        List<String> full = fullVoyages(cargo);
        if (!full.isEmpty()) {
            // **確定できないことを、便の名前とともに伝える。**
            // 「確定できません」だけでは、経路を選び直せばよいことが分からない
            return Result.rejected(
                    "満船の便が含まれています（%s）。経路を選び直してください"
                            .formatted(String.join("、", full)));
        }

        try {
            cargo.confirm();
        } catch (IllegalStateException e) {
            // 経路が割り当てられていない（遷移表 #4 の事前条件）
            return Result.rejected("経路が割り当てられていない予約は確定できません");
        } catch (InvalidBookingStatusTransitionException e) {
            return Result.rejected("この状態の予約は確定できません");
        }

        if (!cargoRepository.update(cargo)) {
            return Result.conflicted();
        }

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("予約確定 bookingId={} actor={}",
                    bookingId.value(), AuditValue.sanitize(actor));
        }
        return Result.confirmed();
    }

    /**
     * 旅程に含まれる便のうち、いま空きが無いものを返す。
     *
     * <p>経路が割り当てられていなければ空を返す。<strong>確定できない理由は
     * 「経路が無い」ことであり、「満船」ではない。</strong> 理由を取り違えると、
     * 経路設計者は空きのある便を探し続けることになる。
     */
    private List<String> fullVoyages(Cargo cargo) {
        if (cargo.cargoItinerary() == null) {
            return List.of();
        }
        List<String> voyageNumbers = cargo.cargoItinerary().legs().stream()
                .map(Leg::voyageNumber)
                .distinct()
                .toList();
        return voyageCapacity.findFullVoyages(
                voyageNumbers,
                cargo.cargoSpecification().weight().kilograms(),
                cargo.bookingId().value().toString());
    }

    /** 確定の結果。 */
    public enum Outcome {
        /** 確定した。 */
        CONFIRMED,
        /** 対象の予約が見つからない。 */
        NOT_FOUND,
        /** 確定できない（経路未割り当て・状態・満船）。 */
        REJECTED,
        /** 他の操作が先行していた（楽観的ロック）。 */
        CONFLICTED
    }

    /**
     * 確定の結果。
     *
     * @param outcome 結果の種別
     * @param reason  確定できなかった理由。確定できた場合は {@code null}
     */
    public record Result(Outcome outcome, String reason) {

        static Result confirmed() {
            return new Result(Outcome.CONFIRMED, null);
        }

        static Result notFound() {
            return new Result(Outcome.NOT_FOUND, null);
        }

        static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, reason);
        }

        static Result conflicted() {
            return new Result(Outcome.CONFLICTED, null);
        }

        public boolean isConfirmed() {
            return outcome == Outcome.CONFIRMED;
        }
    }
}
