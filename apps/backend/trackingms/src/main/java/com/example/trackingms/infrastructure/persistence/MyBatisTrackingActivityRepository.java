package com.example.trackingms.infrastructure.persistence;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingBookingId;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TrackingStatus;
import java.util.Optional;

/** 追跡の保存先（MyBatis）。 */
public class MyBatisTrackingActivityRepository implements TrackingActivityRepository {

    private final TrackingActivityMapper mapper;

    public MyBatisTrackingActivityRepository(TrackingActivityMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * まだ無ければ保存し、すでにあればそれを返す。
     *
     * <p><strong>重複かどうかは一意制約が決める。</strong>事前に読んでから書く形にすると、
     * 同じイベントが同時に 2 通届いたときに双方が「無い」と読んでから書き、後の 1 通が
     * 落ちてデッドレターへ回る。単一の購読者では表面化しないが、並行化した瞬間に壊れる。
     *
     * <p>IT6 で起きるのは<strong>作成だけ</strong>である。状態の更新は US15 で足す。
     * <strong>更新の分岐が無いことを明記する</strong>——書かないと、最初の更新のときに
     * 「常に INSERT する save」の形で壊れる。
     */
    @Override
    public TrackingActivity saveIfAbsent(TrackingActivity activity) {
        mapper.insertIfAbsent(toRecord(activity));
        return findByTrackingNumber(activity.trackingNumber()).orElseThrow();
    }

    /**
     * 状態を更新する。
     *
     * <p><strong>追跡番号で更新する。</strong>id で更新すると、復元していない集約
     * （まだ id を持たないもの）で黙って 0 件更新になる。
     */
    @Override
    public void updateStatus(TrackingActivity activity) {
        mapper.updateStatus(activity.trackingNumber().value(), activity.trackingStatus().name());
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
        row.setTrackingStatus(activity.trackingStatus().name());
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
                TrackingStatus.valueOf(row.getTrackingStatus()),
                Location.of(row.getOriginUnlocode(), row.getOriginName()),
                Location.of(row.getDestinationUnlocode(), row.getDestinationName()),
                row.getArrivalDeadline());
    }
}
