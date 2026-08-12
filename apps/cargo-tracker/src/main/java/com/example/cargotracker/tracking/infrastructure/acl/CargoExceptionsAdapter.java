package com.example.cargotracker.tracking.infrastructure.acl;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.CargoExceptions;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.entities.TrackingExceptionEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link CargoExceptions} の実装（ACL のアダプタ。C31）。
 *
 * <p><strong>返すのは表示のための素の値だけである。</strong> 例外の集約を返すと、
 * Booking が Tracking のドメインを参照することになる（ArchUnit ルール 4）。
 *
 * <p><strong>形式の違う追跡番号を例外にしない。</strong> 予約詳細を開いただけで
 * 500 を返す形を作らない（追跡番号が未発行の予約もある）。
 */
@Component
public class CargoExceptionsAdapter implements CargoExceptions {

    private final TrackingActivityRepository trackingRepository;

    public CargoExceptionsAdapter(TrackingActivityRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    @Override
    public List<ExceptionSummary> findByTrackingNumber(String trackingNumber) {
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
                .map(CargoExceptionsAdapter::toViews)
                .orElseGet(List::of);
    }

    @Override
    public java.util.Set<String> findTrackingNumbersWithException(
            java.util.Collection<String> trackingNumbers) {
        if (trackingNumbers == null || trackingNumbers.isEmpty()) {
            return java.util.Set.of();
        }
        // **1 件ずつ聞かない**（IT13 レビュー C4）。一覧の行数だけ問い合わせが飛ぶ。
        // **形式の違う番号は落とす** — 例外にすると画面が 500 になる
        java.util.Set<String> valid = new java.util.LinkedHashSet<>();
        for (String number : trackingNumbers) {
            if (number == null || number.isBlank()) {
                continue;
            }
            try {
                new TrackingNumber(number);
                valid.add(number);
            } catch (IllegalArgumentException e) {
                // 追跡番号が未発行の予約は日常的にある
            }
        }
        if (valid.isEmpty()) {
            return java.util.Set.of();
        }
        return trackingRepository.findTrackingNumbersWithUnresolvedException(valid);
    }

    private static List<ExceptionSummary> toViews(TrackingActivity tracking) {
        return tracking.exceptions().stream()
                // **発生の新しい順。** いま何が起きているかを先に読む
                .sorted(Comparator.comparing(TrackingExceptionEvent::occurredAt).reversed())
                .map(e -> new ExceptionSummary(
                        e.exceptionType().displayName(),
                        e.occurredAt(),
                        e.description(),
                        e.isResolved(),
                        e.resolutionNotes()))
                .toList();
    }
}
