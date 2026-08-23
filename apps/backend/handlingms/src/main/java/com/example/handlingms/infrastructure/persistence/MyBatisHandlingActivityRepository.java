package com.example.handlingms.infrastructure.persistence;

import com.example.handlingms.application.port.HandlingActivityRepository;
import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.ConsigneeConfirmation;
import com.example.handlingms.domain.model.HandlingActivity;
import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.model.HandlingVoyageNumber;
import com.example.shared.domain.model.Location;
import java.util.List;

/** 荷役の記録の保存先（MyBatis）。 */
public class MyBatisHandlingActivityRepository implements HandlingActivityRepository {

    private final HandlingActivityMapper mapper;

    public MyBatisHandlingActivityRepository(HandlingActivityMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 記録する。
     *
     * <p><strong>作成しか無い。</strong>荷役は実際に起きた作業の記録であり、あとから直す
     * ものではない。<strong>更新の分岐が無いことを明記する</strong>——書かないと、最初の
     * 更新のときに「常に INSERT する save」の形で壊れる。
     *
     * <p>読み戻して返すのは、地点の名称と採番された id を揃えるためである。呼び出し側が
     * 書いた値をそのまま返すと、<strong>保存できていない項目に気づけない</strong>。
     */
    @Override
    public HandlingActivity register(HandlingActivity activity) {
        HandlingActivityRecord row = toRecord(activity);
        mapper.insert(row);
        return toDomain(mapper.findById(row.getId()));
    }

    @Override
    public List<HandlingActivity> findByBookingId(CargoBookingId bookingId, int limit) {
        return mapper.findByBookingId(bookingId.value(), limit).stream()
                .map(MyBatisHandlingActivityRepository::toDomain)
                .toList();
    }

    @Override
    public boolean existsSameActivity(CargoBookingId bookingId, HandlingType type,
            String locationUnLocode, java.time.Instant completionTime) {
        return mapper.countSameActivity(bookingId.value(), type.name(), locationUnLocode,
                completionTime) > 0;
    }

    private static HandlingActivityRecord toRecord(HandlingActivity activity) {
        HandlingActivityRecord row = new HandlingActivityRecord();
        row.setBookingId(activity.bookingId().value());
        row.setEventType(activity.type().name());
        row.setEventCompletionTime(activity.completionTime());
        row.setLocationUnlocode(activity.location().unLocode());
        row.setVoyageNumber(activity.voyageNumber().map(HandlingVoyageNumber::value).orElse(null));
        row.setOperatorName(activity.operatorName());
        row.setConsigneeConfirmation(activity.consigneeConfirmation()
                .map(ConsigneeConfirmation::confirmedBy).orElse(null));
        row.setOffRoute(activity.offRoute());
        return row;
    }

    /** 復元では検査しない。列が無かったころの行が読めなくなる。 */
    private static HandlingActivity toDomain(HandlingActivityRecord row) {
        return HandlingActivity.restore(row.getId(),
                CargoBookingId.restore(row.getBookingId()),
                HandlingType.valueOf(row.getEventType()),
                Location.of(row.getLocationUnlocode(), row.getLocationName()),
                row.getEventCompletionTime(),
                row.getOperatorName(),
                HandlingVoyageNumber.restoreNullable(row.getVoyageNumber()),
                ConsigneeConfirmation.restoreNullable(row.getConsigneeConfirmation()),
                row.isOffRoute());
    }
}
