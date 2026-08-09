package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.application.internal.outboundservices.acl.CustomsStatuses;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.PortNames;
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
    private final PortNames portNames;
    private final CustomsStatuses customsStatuses;

    public TrackingInquiryService(
            TrackingActivityRepository trackingRepository,
            PortNames portNames,
            CustomsStatuses customsStatuses) {
        this.trackingRepository = trackingRepository;
        this.portNames = portNames;
        this.customsStatuses = customsStatuses;
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
        List<TrackingActivityEvent> ordered = tracking.events().stream()
                // **新しい順に出す。** 利用者が知りたいのは最後に何が起きたかである
                .sorted(Comparator.comparing(TrackingActivityEvent::occurredAt).reversed())
                .toList();

        // **目的地は追跡が自分で持つ**（ADR-012）。Booking へ問い合わせない
        String destination = tracking.destination().location() == null
                ? "" : tracking.destination().location().unlocode();
        String current = ordered.isEmpty() ? "" : ordered.getFirst().location().unlocode();

        // **港の名前をまとめて引く。** 1 件ずつ引くと履歴の件数だけ問い合わせが増える
        var names = portNames.findNames(java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(destination, current),
                        ordered.stream().map(e -> e.location().unlocode()))
                .filter(code -> !code.isBlank())
                .distinct()
                .toList());

        return new TrackingInquiryView(
                tracking.trackingNumber().value(),
                tracking.transportStatus().displayName(),
                tracking.transportStatus().badgeClass(),
                withName(current, names),
                withName(destination, names),
                tracking.destination().estimatedArrival(),
                // **通関は Handling の持ち物である。** SQL で JOIN せず ACL ポートで引く
                customsStatuses.findByTrackingNumber(tracking.trackingNumber().value())
                        .map(c -> new TrackingInquiryView.CustomsStatusView(
                                c.statusLabel(), c.allowsClaim()))
                        .orElse(null),
                ordered.stream()
                        .map(event -> new TrackingInquiryView.TrackingEventView(
                                event.occurredAt(),
                                event.type().displayName(),
                                withName(event.location().unlocode(), names),
                                event.manual(),
                                event.recordedBy()))
                        .toList());
    }

    /**
     * {@code JPOSA（大阪）} の形にする。
     *
     * <p><strong>コードを消さない。</strong> 港湾名だけにすると、荷役作業員や
     * 経路設計者が普段使っているコードと突き合わせられなくなる。
     * <strong>マスタに無いコードはそのまま出す</strong>（名前が無いことを
     * 「不明」と書くと、コードすら読めなくなる）。
     */
    private static String withName(String code, java.util.Map<String, String> names) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String name = names.get(code);
        return name == null || name.isBlank() ? code : "%s（%s）".formatted(code, name);
    }
}
