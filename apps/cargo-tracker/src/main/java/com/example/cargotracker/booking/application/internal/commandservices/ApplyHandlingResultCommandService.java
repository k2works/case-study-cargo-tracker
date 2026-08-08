package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷役の結果を予約に反映する（US15）。
 *
 * <p>反映するのは 2 つである。
 *
 * <ul>
 *   <li><strong>誤配</strong>（荷役ビジネスルール 1）。積込・荷降しが予定ルートから
 *       外れた場合にのみ経路状態を {@code MISROUTED} にする</li>
 *   <li><strong>輸送の開始</strong>（遷移表 #6）。最初の積込で {@code IN_TRANSIT} へ</li>
 * </ul>
 *
 * <p><strong>解釈するのは予約である。</strong> 荷役は「予定と違った」「積み込んだ」を
 * 伝えるだけであり、それが誤配にあたるか・状態を進めるかは予約が決める（ADR-009）。
 *
 * <p><strong>新しいトランザクションで書く</strong>（{@code REQUIRES_NEW}）。
 */
@Service
public class ApplyHandlingResultCommandService {

    private final CargoRepository cargoRepository;

    public ApplyHandlingResultCommandService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    /**
     * 荷役の結果を反映する。
     *
     * @param bookingId 予約 ID
     * @param misrouted 予定ルートから外れた作業か
     * @param loaded    積込だったか（最初の積込なら輸送を開始する）
     * @return 反映の結果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result apply(UUID bookingId, boolean misrouted, boolean loaded) {
        Optional<Cargo> found = cargoRepository.findById(new BookingId(bookingId));
        if (found.isEmpty()) {
            return Result.NOT_FOUND;
        }
        Cargo cargo = found.get();

        if (misrouted) {
            cargo.markMisrouted();
            if (!cargoRepository.updateRouting(cargo)) {
                return Result.CONFLICTED;
            }
        }

        if (!loaded) {
            return Result.APPLIED;
        }

        // **述語で確かめてから進める。** 積込は輸送中にも起きる（積み替え）ため、
        // そのたびに遷移を試みると正しい荷役の記録が拒否される。
        // 誤配の反映で version が進んでいるため読み直す
        Cargo latest = cargoRepository.findById(new BookingId(bookingId)).orElse(null);
        if (latest == null || !latest.canStartTransport()) {
            return Result.APPLIED;
        }
        latest.startTransport();
        return cargoRepository.update(latest) ? Result.APPLIED : Result.CONFLICTED;
    }

    /** 反映の結果。 */
    public enum Result {
        /** 反映した（進める必要が無かった場合を含む）。 */
        APPLIED,
        /** 予約が見つからない。 */
        NOT_FOUND,
        /** 他の更新が先行していた（楽観的ロック）。 */
        CONFLICTED
    }
}
