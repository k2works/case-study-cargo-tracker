package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.handling.domain.model.CargoBookingId;
import com.example.cargotracker.handling.domain.model.HandlingActivity;
import com.example.cargotracker.handling.domain.model.HandlingType;
import com.example.cargotracker.handling.domain.model.ClaimConfirmation;
import com.example.cargotracker.handling.domain.model.ClaimConfirmationMethod;
import com.example.cargotracker.handling.domain.model.HandledCargo;
import com.example.cargotracker.handling.domain.model.HandlingDetails;
import com.example.cargotracker.handling.domain.model.HandlingVoyageNumber;
import com.example.cargotracker.handling.domain.model.ScannedTrackingNumber;
import com.example.cargotracker.handling.domain.repository.HandlingActivityRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** {@link HandlingActivityRepository} の MyBatis 実装（出力アダプタ）。 */
@Repository
public class MyBatisHandlingActivityRepository implements HandlingActivityRepository {

    private final HandlingMapper mapper;

    public MyBatisHandlingActivityRepository(HandlingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(HandlingActivity activity) {
        mapper.insert(toRecord(activity));
    }

    @Override
    public List<HandlingActivity> findByBookingId(CargoBookingId bookingId) {
        return mapper.findByBookingId(bookingId.value()).stream()
                .map(MyBatisHandlingActivityRepository::toDomain)
                .toList();
    }

    @Override
    public List<HandlingActivity> findRecent(int limit) {
        return mapper.findRecent(limit).stream()
                .map(MyBatisHandlingActivityRepository::toDomain)
                .toList();
    }

    private static HandlingActivity toDomain(HandlingActivityRecord row) {
        return HandlingActivity.reconstruct(
                new HandledCargo(
                        // **IT6 以前の行は番号を持たない**（V13 で追加した列）。
                        // 後から埋めると「記録されていたこと」と区別がつかなくなるため、
                        // 記録が無いことを表す印を置く
                        new ScannedTrackingNumber(row.getTrackingNumber() == null
                                ? "(記録なし)" : row.getTrackingNumber()),
                        new CargoBookingId(row.getBookingId())),
                HandlingDetails.of(
                        HandlingType.valueOf(row.getEventType()),
                        // **読み戻しで落とすと、積込がどの便のものか分からなくなる**
                        row.getVoyageNumber() == null
                                ? null : new HandlingVoyageNumber(row.getVoyageNumber()),
                        // 引取以外は確認を持たない（V14 の CHECK 制約で DB 側も守る）
                        row.getClaimConfirmationMethod() == null ? null : new ClaimConfirmation(
                                ClaimConfirmationMethod.valueOf(
                                        row.getClaimConfirmationMethod()),
                                row.getClaimConfirmationCode(),
                                row.getClaimConsigneeName())),
                row.getEventCompletionTime(),
                Location.of(row.getLocationUnlocode()),
                row.getNote(),
                row.getOperatorName(),
                row.getVersion());
    }

    private static HandlingActivityRecord toRecord(HandlingActivity activity) {
        HandlingActivityRecord row = new HandlingActivityRecord();
        row.setBookingId(activity.cargoBookingId().value());
        row.setEventType(activity.type().name());
        row.setEventCompletionTime(activity.completionTime());
        row.setLocationUnlocode(activity.location().unlocode());
        row.setVoyageNumber(
                activity.voyageNumber() == null ? null : activity.voyageNumber().value());
        row.setTrackingNumber(activity.scannedTrackingNumber().value());
        ClaimConfirmation confirmation = activity.claimConfirmation();
        row.setClaimConfirmationMethod(
                confirmation == null ? null : confirmation.method().name());
        row.setClaimConfirmationCode(confirmation == null ? null : confirmation.code());
        row.setClaimConsigneeName(
                confirmation == null ? null : confirmation.consigneeName());
        row.setNote(activity.note());
        row.setOperatorName(activity.operatorName());
        row.setVersion(activity.version());
        return row;
    }

    @Override
    public java.util.Optional<CancellableHandling> findCancellable(long handlingActivityId) {
        HandlingActivityRecord row = mapper.findById(handlingActivityId);
        return row == null ? java.util.Optional.empty() : java.util.Optional.of(
                new CancellableHandling(
                        row.getId(), row.getBookingId(), row.getTrackingNumber(),
                        row.getCancelledAt() != null));
    }

    @Override
    public boolean markCancelled(long handlingActivityId, java.time.Instant at, String by) {
        HandlingActivityRecord row = new HandlingActivityRecord();
        row.setId(handlingActivityId);
        row.setCancelledAt(at);
        row.setCancelledBy(by);
        return mapper.markCancelled(row) == 1;
    }
}
