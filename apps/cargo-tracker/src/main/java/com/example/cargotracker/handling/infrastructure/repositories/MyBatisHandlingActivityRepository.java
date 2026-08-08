package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.handling.domain.model.CargoBookingId;
import com.example.cargotracker.handling.domain.model.HandlingActivity;
import com.example.cargotracker.handling.domain.model.HandlingType;
import com.example.cargotracker.handling.domain.model.HandledCargo;
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
                HandlingType.valueOf(row.getEventType()),
                row.getEventCompletionTime(),
                Location.of(row.getLocationUnlocode()),
                // **読み戻しで落とすと、積込がどの便のものか分からなくなる**
                row.getVoyageNumber() == null
                        ? null : new HandlingVoyageNumber(row.getVoyageNumber()),
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
        row.setOperatorName(activity.operatorName());
        row.setVersion(activity.version());
        return row;
    }
}
