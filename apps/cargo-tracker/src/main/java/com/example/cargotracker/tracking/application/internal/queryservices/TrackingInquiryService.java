package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.application.internal.outboundservices.acl
        .CargoArrivalEstimates;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 追跡照会（US18）。
 *
 * <p><strong>公開画面と認証つき画面が同じ答えを返す。</strong> 見せる範囲を
 * 画面ごとに変えると、片方にだけ個人情報が混ざる形が生まれる。
 * 追跡番号を知っている相手に見せてよい情報だけをここで組み立てる。
 *
 * <p><strong>存在しない番号と形式の違う番号を区別しない。</strong> 区別すると、
 * 「形式は正しいが存在しない」という答えが返り、<strong>番号の総当たりで
 * 貨物の有無を確かめられる</strong>。
 */
@Service
public class TrackingInquiryService {

    private final TrackingActivityRepository trackingRepository;
    private final CargoArrivalEstimates arrivalEstimates;

    public TrackingInquiryService(
            TrackingActivityRepository trackingRepository,
            CargoArrivalEstimates arrivalEstimates) {
        this.trackingRepository = trackingRepository;
        this.arrivalEstimates = arrivalEstimates;
    }

    /**
     * 追跡番号から現在状況を引き当てる。
     *
     * @param trackingNumber 追跡番号（利用者の入力そのまま）
     * @return 見つからなければ空。<strong>形式の誤りも「見つからない」として扱う</strong>
     */
    public Optional<TrackingInquiryView> findByTrackingNumber(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Optional.empty();
        }
        TrackingNumber number;
        try {
            number = new TrackingNumber(trackingNumber.strip().toUpperCase(
                    java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // **形式の誤りも「見つからない」として返す。**
            // 「形式は正しいが存在しない」と答えると、総当たりで有無を確かめられる
            return Optional.empty();
        }
        return trackingRepository.findByTrackingNumber(number).map(this::toView);
    }

    private TrackingInquiryView toView(TrackingActivity tracking) {
        var estimate = arrivalEstimates
                .findByBookingId(tracking.bookingId().value().toString());

        List<TrackingActivityEvent> ordered = tracking.events().stream()
                // **新しい順に出す。** 利用者が知りたいのは最後に何が起きたかである
                .sorted(Comparator.comparing(TrackingActivityEvent::occurredAt).reversed())
                .toList();

        return new TrackingInquiryView(
                tracking.trackingNumber().value(),
                tracking.transportStatus().displayName(),
                tracking.transportStatus().badgeClass(),
                ordered.isEmpty() ? "" : ordered.getFirst().location().unlocode(),
                estimate.map(CargoArrivalEstimates.CargoArrivalEstimate::destination)
                        .orElse(""),
                estimate.map(CargoArrivalEstimates.CargoArrivalEstimate::estimatedArrival)
                        .orElse(null),
                ordered.stream()
                        .map(event -> new TrackingInquiryView.TrackingEventView(
                                event.occurredAt(),
                                event.type().displayName(),
                                event.location().unlocode()))
                        .toList());
    }
}
