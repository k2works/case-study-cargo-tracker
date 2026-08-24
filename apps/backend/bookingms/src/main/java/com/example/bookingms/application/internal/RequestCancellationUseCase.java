package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.CancellationRequestRepository;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.domain.model.CancellationRequest;
import com.example.bookingms.domain.model.Cargo;
import java.time.Clock;
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
public class RequestCancellationUseCase {

    private final CargoRepository cargoes;
    private final CancellationRequestRepository cancellations;
    private final Clock clock;

    public RequestCancellationUseCase(CargoRepository cargoes,
            CancellationRequestRepository cancellations, Clock clock) {
        this.cargoes = cargoes;
        this.cancellations = cancellations;
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
            cargoes.save(cargo.cancel());
        }
        return new CancellationOutcome(saved, inTransit);
    }
}
