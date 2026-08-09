package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.booking.domain.model.MisrouteDetection;
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

    /** 最初の積込で輸送が始まる（遷移表 #6）。 */
    private static final String LOAD = "LOAD";

    /** 引取で配送が完了する（遷移表 #7。US16）。 */
    private static final String CLAIM = "CLAIM";

    private final CargoRepository cargoRepository;

    public ApplyHandlingResultCommandService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    /**
     * 荷役の結果を反映する。
     *
     * @param bookingId    予約 ID
     * @param misrouted        予定ルートから外れた作業か
     * @param locationUnlocode 作業場所。**誤配なら貨物の現在地になる**
     * @param completionTime   作業日時。**誤配を検知した時点である**
     * @param handlingType 荷役種別の名前（{@code LOAD} で輸送開始、{@code CLAIM} で配送完了）
     * @return 反映の結果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result apply(
            UUID bookingId, boolean misrouted, String handlingType,
            String locationUnlocode, java.time.Instant completionTime) {
        Optional<Cargo> found = cargoRepository.findById(new BookingId(bookingId));
        if (found.isEmpty()) {
            return Result.NOT_FOUND;
        }

        if (misrouted) {
            Result routing = applyMisroute(found.get(),
                    MisrouteDetection.reconstruct(
                            locationUnlocode == null ? null : Location.of(locationUnlocode),
                            completionTime));
            if (routing != Result.APPLIED) {
                return routing;
            }
        }
        return advanceStatus(bookingId, handlingType);
    }

    /** 誤配を経路状態に反映する（荷役ビジネスルール 1）。 */
    private Result applyMisroute(Cargo cargo, MisrouteDetection detection) {
        cargo.markMisrouted(detection);
        return cargoRepository.updateRouting(cargo) ? Result.APPLIED : Result.CONFLICTED;
    }

    /**
     * 荷役の種別に応じて予約状態を進める。
     *
     * <p><strong>述語で確かめてから進める。</strong> 積込は輸送中にも起きる（積み替え）、
     * 引取も二重登録がありうる。そのたびに遷移を試みると正しい荷役の記録が拒否される。
     * <strong>進める必要が無いことは、失敗ではない。</strong>
     */
    private Result advanceStatus(UUID bookingId, String handlingType) {
        // 誤配の反映で version が進んでいるため読み直す
        Cargo latest = cargoRepository.findById(new BookingId(bookingId)).orElse(null);
        if (latest == null || !advance(latest, handlingType)) {
            return Result.APPLIED;
        }
        return cargoRepository.update(latest) ? Result.APPLIED : Result.CONFLICTED;
    }

    /** 進めたなら {@code true}。進める必要が無ければ {@code false}。 */
    private static boolean advance(Cargo cargo, String handlingType) {
        return switch (handlingType) {
            case LOAD -> advanceIf(cargo.canStartTransport(), cargo::startTransport);
            case CLAIM -> advanceIf(cargo.canCompleteDelivery(), cargo::completeDelivery);
            default -> false;
        };
    }

    private static boolean advanceIf(boolean allowed, Runnable transition) {
        if (allowed) {
            transition.run();
        }
        return allowed;
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
