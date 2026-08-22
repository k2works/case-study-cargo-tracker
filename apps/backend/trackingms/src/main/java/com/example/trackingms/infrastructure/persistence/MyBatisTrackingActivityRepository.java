package com.example.trackingms.infrastructure.persistence;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingBookingId;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TransportStatus;
import java.util.Optional;

/** 追跡の保存先（MyBatis）。 */
public class MyBatisTrackingActivityRepository implements TrackingActivityRepository {

    private final TrackingActivityMapper mapper;

    public MyBatisTrackingActivityRepository(TrackingActivityMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 保存する。
     *
     * <p>IT6 で起きるのは<strong>作成だけ</strong>である。状態の更新は US15 以降で足す。
     * <strong>更新の分岐が無いことを明記する</strong>——書かないと、最初の更新のときに
     * 「常に INSERT する save」の形で壊れる（他プロジェクトで実際に起きた形）。
     */
    @Override
    public TrackingActivity save(TrackingActivity activity) {
        TrackingActivityRecord row = toRecord(activity);
        mapper.insert(row);
        return findByTrackingNumber(activity.trackingNumber()).orElseThrow();
    }

    @Override
    public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
        return Optional.ofNullable(mapper.findByTrackingNumber(trackingNumber.value()))
                .map(MyBatisTrackingActivityRepository::toDomain);
    }

    private static TrackingActivityRecord toRecord(TrackingActivity activity) {
        TrackingActivityRecord row = new TrackingActivityRecord();
        row.setTrackingNumber(activity.trackingNumber().value());
        row.setBookingId(activity.bookingId().value());
        row.setTransportStatus(activity.transportStatus().name());
        row.setOriginUnlocode(activity.origin().unLocode());
        row.setDestinationUnlocode(activity.destination().unLocode());
        row.setArrivalDeadline(activity.arrivalDeadline());
        return row;
    }

    /** 復元では検査しない。列が無かったころの行が読めなくなる。 */
    private static TrackingActivity toDomain(TrackingActivityRecord row) {
        return TrackingActivity.restore(row.getId(),
                TrackingNumber.restore(row.getTrackingNumber()),
                TrackingBookingId.restore(row.getBookingId()),
                TransportStatus.valueOf(row.getTransportStatus()),
                Location.of(row.getOriginUnlocode(), row.getOriginName()),
                Location.of(row.getDestinationUnlocode(), row.getDestinationName()),
                row.getArrivalDeadline());
    }
}
