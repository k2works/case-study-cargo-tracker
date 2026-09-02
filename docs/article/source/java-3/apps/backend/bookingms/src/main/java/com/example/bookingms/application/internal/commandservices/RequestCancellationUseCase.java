package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.application.internal.outboundservices.acl.CargoCancelled;
import com.example.bookingms.application.internal.outboundservices.acl.CargoEventNotifier;
import com.example.bookingms.domain.repository.CancellationRequestRepository;
import com.example.bookingms.domain.repository.CargoRepository;
import com.example.bookingms.domain.repository.CargoSummary;
import com.example.bookingms.domain.model.aggregates.CancellationRequest;
import com.example.bookingms.domain.model.aggregates.Cargo;
import java.time.Clock;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

/**
 * キャンセルを申請する（US30-1・US30-2・US30-3）。
 *
 * <p><strong>輸送開始前と輸送中で結果が違う。</strong>輸送開始前はその場で確定し、
 * 輸送中は追跡管理者の承認を待つ——貨物が船の上にあり、<strong>どこで降ろすかを
 * 決めないとキャンセルできない</strong>。
 *
 * <p><strong>判断は集約が答える</strong>（{@code Cargo#isInTransit}）。ここで状態名を
 * 見比べると、規則が 2 か所に分かれる。
 */
@Service
public class RequestCancellationUseCase {

    private final CargoRepository cargoes;
    private final CancellationRequestRepository cancellations;
    private final CargoEventNotifier events;
    private final Clock clock;

    public RequestCancellationUseCase(CargoRepository cargoes,
            CancellationRequestRepository cancellations, CargoEventNotifier events, Clock clock) {
        this.cargoes = cargoes;
        this.cancellations = cancellations;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 申請する。
     *
     * @throws IllegalArgumentException 予約が見つからないとき
     * @throws IllegalStateException 承認待ちの申請があるとき、またはキャンセルできない状態のとき
     */
    @Transactional
    public CancellationOutcome request(String bookingId, String reason, String requestedBy) {
        Cargo cargo = cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "予約が見つかりません: " + bookingId));

        // **判断待ちの申請は貨物あたり 1 件まで。**2 件あると、どちらを承認するか決まらない
        cancellations.findAwaitingByCargoId(cargo.id()).ifPresent(awaiting -> {
            throw new IllegalStateException("この予約には承認待ちのキャンセル申請があります");
        });
        // 断る理由は集約が持つ。ここで組み立て直さない
        cargo.reasonCannotCancel().ifPresent(reasonCannot -> {
            throw new IllegalStateException(reasonCannot);
        });

        boolean inTransit = cargo.isInTransit();
        CancellationRequest saved = cancellations.save(CancellationRequest.request(
                cargo.id(), reason, requestedBy, clock.instant(), cargo.bookingStatus(),
                inTransit));

        if (!inTransit) {
            // **輸送開始前は承認を待つ理由が無い。**貨物はまだ動いていない
            Cargo cancelled = cargoes.save(cargo.cancel());

            // **知らせ方を承認の経路と揃える**（[ADR-025] 決定 3）。
            // 輸送前でも追跡番号は出ていることがあり、荷主はそれを見ている
            // ——知らせないと、自分が申し入れて確定したキャンセルを画面で否定される。
            // **理由は載せない**（公開の追跡照会へ流れる経路である）
            cancelled.trackingNumber().ifPresent(trackingNumber ->
                    events.cargoCancelled(new CargoCancelled(trackingNumber.value(),
                            cancelled.bookingId().map(Object::toString).orElse(null),
                            clock.instant(), clock.instant())));
        }
        return new CancellationOutcome(saved, inTransit);
    }
}
