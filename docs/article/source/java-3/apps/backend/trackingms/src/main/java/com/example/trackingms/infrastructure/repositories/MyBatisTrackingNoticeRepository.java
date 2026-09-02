package com.example.trackingms.infrastructure.repositories;

import com.example.trackingms.domain.repository.TrackingNoticeRepository;
import com.example.trackingms.domain.model.valueobjects.TrackingNotice;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import java.util.List;

/** 通知した事実の保存先（MyBatis）。 */
public class MyBatisTrackingNoticeRepository implements TrackingNoticeRepository {

    private final TrackingNoticeMapper mapper;

    public MyBatisTrackingNoticeRepository(TrackingNoticeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(TrackingNumber trackingNumber, TrackingNotice notice) {
        mapper.insert(trackingNumber.value(), notice.message(), notice.noticedAt());
    }

    @Override
    public List<TrackingNotice> findByTrackingNumber(TrackingNumber trackingNumber, int limit) {
        return mapper.findByTrackingNumber(trackingNumber.value(), limit).stream()
                .map(row -> new TrackingNotice(row.getNoticedAt(), row.getMessage()))
                .toList();
    }
}
