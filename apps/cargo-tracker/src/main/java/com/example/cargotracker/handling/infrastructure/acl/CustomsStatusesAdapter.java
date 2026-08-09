package com.example.cargotracker.handling.infrastructure.acl;

import com.example.cargotracker.handling.application.internal.outboundservices.acl.CargoSnapshots;
import com.example.cargotracker.handling.domain.model.CargoSnapshot;
import com.example.cargotracker.handling.domain.model.CustomsDeclaration;
import com.example.cargotracker.handling.domain.repository.CustomsDeclarationRepository;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.CustomsStatuses;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link CustomsStatuses} の実装（ACL のアダプタ。C30）。
 *
 * <p><strong>「申告があるか」ではなく「通関が要るか」で判断する。</strong>
 * 申告の有無は手続きの進み具合であって、通関の要否ではない。
 * 要否は {@link CargoSnapshot#requiresCustoms()} が持つ（C29 と同じ述語を使う）。
 *
 * <p><strong>要るのに申告が無い状態を空欄にしない。</strong> 空欄は「問題なし」と
 * 読まれる。荷受人にとってもっとも知りたいのは、手続きが始まっていないことである。
 */
@Component
public class CustomsStatusesAdapter implements CustomsStatuses {

    /** 申告がまだ出ていないときのラベル。**空欄にしない。** */
    private static final String NOT_STARTED = "手続き前";

    private final CargoSnapshots cargoSnapshots;
    private final CustomsDeclarationRepository declarationRepository;

    public CustomsStatusesAdapter(
            CargoSnapshots cargoSnapshots,
            CustomsDeclarationRepository declarationRepository) {
        this.cargoSnapshots = cargoSnapshots;
        this.declarationRepository = declarationRepository;
    }

    @Override
    public Optional<CustomsStatusSummary> findByTrackingNumber(String trackingNumber) {
        Optional<CargoSnapshots.Snapshot> snapshot =
                cargoSnapshots.findByTrackingNumber(trackingNumber);
        if (snapshot.isEmpty() || !requiresCustoms(snapshot.get())) {
            return Optional.empty();
        }
        Optional<CustomsDeclaration> declaration =
                declarationRepository.findByTrackingNumber(trackingNumber);
        return Optional.of(declaration
                .map(d -> new CustomsStatusSummary(
                        d.status().displayName(), d.status().allowsClaim()))
                .orElseGet(() -> new CustomsStatusSummary(NOT_STARTED, false)));
    }

    /** 要否の判断は C29 と同じ述語を通す。**ここで国コードを比べ直さない。** */
    private static boolean requiresCustoms(CargoSnapshots.Snapshot snapshot) {
        return new CargoSnapshot(
                snapshot.bookingId(), snapshot.origin(), snapshot.destination(),
                snapshot.consigneeName(), List.of())
                .requiresCustoms();
    }
}
