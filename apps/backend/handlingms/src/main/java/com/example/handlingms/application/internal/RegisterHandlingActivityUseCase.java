package com.example.handlingms.application.internal;

import com.example.handlingms.application.port.CargoSnapshotFinder;
import com.example.handlingms.application.port.HandlingActivityRegistered;
import com.example.handlingms.application.port.HandlingActivityRepository;
import com.example.handlingms.application.port.HandlingEventNotifier;
import com.example.handlingms.application.port.LocationRepository;
import com.example.handlingms.domain.model.CargoSnapshot;
import com.example.handlingms.domain.model.ConsigneeConfirmation;
import com.example.handlingms.domain.model.HandlingActivity;
import com.example.handlingms.domain.model.HandlingTrackingNumber;
import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.model.HandlingVoyageNumber;
import com.example.shared.domain.model.Location;
import java.time.Clock;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷役作業を記録する（US15・US16・[ADR-023]）。
 *
 * <p><strong>トランザクションの境目はここに置く。</strong>リポジトリの中に置くと、
 * 保存とイベントの発行が別のトランザクションになり、[ADR-022] 決定 6 の「コミット後に
 * 発行する」機構が素通りする（IT6 で実際に起きた形）。
 */
public class RegisterHandlingActivityUseCase {

    private final CargoSnapshotFinder cargoes;
    private final LocationRepository locations;
    private final HandlingActivityRepository activities;
    private final HandlingEventNotifier notifier;
    private final Clock clock;

    public RegisterHandlingActivityUseCase(CargoSnapshotFinder cargoes,
            LocationRepository locations, HandlingActivityRepository activities,
            HandlingEventNotifier notifier, Clock clock) {
        this.cargoes = cargoes;
        this.locations = locations;
        this.activities = activities;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * 記録する。
     *
     * @return 記録した荷役。追跡番号が見つからなければ空
     * @throws IllegalArgumentException 種別の要件を満たしていないとき、または作業場所が
     *     地点マスタに無いとき
     */
    @Transactional
    public Optional<HandlingActivity> register(RegisterHandlingActivityCommand command) {
        HandlingTrackingNumber trackingNumber =
                HandlingTrackingNumber.of(command.trackingNumber());
        Optional<CargoSnapshot> cargo = cargoes.findByTrackingNumber(trackingNumber);
        if (cargo.isEmpty()) {
            return Optional.empty();
        }

        HandlingActivity registered = activities.register(HandlingActivity.register(
                cargo.orElseThrow(),
                HandlingType.valueOf(command.type()),
                requireLocation(command.locationUnLocode()),
                command.completionTime(),
                command.operatorName(),
                command.voyageNumber() == null || command.voyageNumber().isBlank()
                        ? null : HandlingVoyageNumber.of(command.voyageNumber()),
                command.consigneeConfirmation() == null
                        || command.consigneeConfirmation().isBlank()
                        ? null : ConsigneeConfirmation.of(command.consigneeConfirmation())));

        notifier.handlingActivityRegistered(new HandlingActivityRegistered(
                trackingNumber.value(),
                registered.bookingId().value(),
                registered.type().name(),
                registered.location().unLocode(),
                registered.completionTime(),
                registered.voyageNumber().map(HandlingVoyageNumber::value).orElse(null),
                registered.offRoute(),
                clock.instant()));

        return Optional.of(registered);
    }

    /**
     * 作業場所は地点マスタから引く（US15-3）。
     *
     * <p>自由入力を通すと、綴りの揺れた港が記録に入り、照合が働かなくなる。
     */
    private Location requireLocation(String unLocode) {
        return locations.findByUnLocode(unLocode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "作業場所が見つかりません: " + unLocode));
    }
}
