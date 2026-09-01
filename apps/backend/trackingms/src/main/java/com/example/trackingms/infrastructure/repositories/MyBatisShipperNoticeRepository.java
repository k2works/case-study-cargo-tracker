package com.example.trackingms.infrastructure.repositories;

import com.example.trackingms.domain.model.valueobjects.NoticeWatermark;
import com.example.trackingms.domain.model.valueobjects.ShipperNotice;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.repository.NoticeWatermarkRepository;
import com.example.trackingms.domain.repository.ShipperNoticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;

/** 荷主へ届ける知らせと、読んだ位置（MyBatis）。 */
public class MyBatisShipperNoticeRepository
        implements ShipperNoticeRepository, NoticeWatermarkRepository {

    private final ShipperNoticeMapper mapper;
    private final Clock clock;

    public MyBatisShipperNoticeRepository(ShipperNoticeMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public List<ShipperNotice> findNewerThan(List<TrackingNumber> trackingNumbers,
            long lastNoticeId, int limit) {
        if (trackingNumbers.isEmpty()) {
            // **空で問い合わせない。** `IN ()` は構文として成り立たない
            return List.of();
        }
        return mapper.findNewerThan(trackingNumbers.stream().map(TrackingNumber::value).toList(),
                        lastNoticeId, limit).stream()
                .map(row -> new ShipperNotice(row.getId(),
                        TrackingNumber.of(row.getTrackingNumber()), row.getNoticedAt(),
                        row.getMessage()))
                .toList();
    }

    @Override
    public NoticeWatermark find(String username) {
        Long stored = mapper.selectWatermark(username);
        return stored == null ? NoticeWatermark.unread() : NoticeWatermark.of(stored);
    }

    /**
     * <strong>進めるか、初めて置くかのどちらかである。</strong>
     *
     * <p>更新が 0 行のときは「行が無い」か「すでに先へ進んでいる」のどちらか。
     * 前者なら入れる——同時に入れられたら一意制約が裁くので、もう一度進めれば済む。
     */
    @Override
    public void save(String username, NoticeWatermark watermark) {
        Instant now = clock.instant();
        if (mapper.advanceWatermark(username, watermark.lastNoticeId(), now) > 0) {
            return;
        }
        if (mapper.selectWatermark(username) != null) {
            return;
        }
        try {
            mapper.insertWatermark(username, watermark.lastNoticeId(), now);
        } catch (DuplicateKeyException _) {
            mapper.advanceWatermark(username, watermark.lastNoticeId(), now);
        }
    }
}
