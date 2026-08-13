package com.example.cargotracker.tracking.infrastructure.acl;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .CargoExceptionRecordsPort;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.entities.TrackingExceptionEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link CargoExceptionRecordsPort} の実装（ACL のアダプタ。IT13 レビュー C3）。
 *
 * <p><strong>Booking 向けのアダプタと別に置く。</strong> 返す形が違う（対応内容は
 * 請求では使わない）ため 1 つにまとめると、どちらかの都合で他方の画面が変わる。
 *
 * <p><strong>形式の違う追跡番号を例外にしない。</strong> 請求書を開いただけで
 * 500 になる形を作らない。
 */
@Component
public class CargoExceptionRecordsAdapter implements CargoExceptionRecordsPort {

    private final TrackingActivityRepository trackingRepository;

    public CargoExceptionRecordsAdapter(TrackingActivityRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    @Override
    public List<ExceptionRecord> findByTrackingNumber(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return List.of();
        }
        TrackingNumber number;
        try {
            number = new TrackingNumber(trackingNumber);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return trackingRepository.findByTrackingNumber(number)
                .map(CargoExceptionRecordsAdapter::toRecords)
                .orElseGet(List::of);
    }

    private static List<ExceptionRecord> toRecords(TrackingActivity tracking) {
        return tracking.exceptions().stream()
                // **発生の新しい順。** いま何が起きているかを先に読む
                .sorted(Comparator.comparing(TrackingExceptionEvent::occurredAt).reversed())
                .map(e -> new ExceptionRecord(
                        e.exceptionType().displayName(),
                        e.occurredAt(),
                        e.description(),
                        e.isResolved()))
                .toList();
    }
}
