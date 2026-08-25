package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.CancellationRequestRepository;
import com.example.bookingms.application.port.CargoCancelled;
import com.example.bookingms.application.port.CargoEventNotifier;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.domain.model.CancellationRequest;
import com.example.bookingms.domain.model.Cargo;
import java.time.Clock;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送中のキャンセルを承認・却下する（US30-5・US30-7）。
 *
 * <p><strong>追跡管理者の業務である。</strong>申請するのは営業であり、自分の申請を
 * 自分で承認できると承認の意味が無くなる（認可は入口が守る）。
 */
public class DecideCancellationUseCase {

    /** 承認待ちの一覧に出す上限。**朝の一覧としてこれ以上は読めない**。 */
    public static final int AWAITING_LIMIT = 100;

    private final CargoRepository cargoes;
    private final CancellationRequestRepository cancellations;
    private final CargoEventNotifier events;
    private final Clock clock;

    public DecideCancellationUseCase(CargoRepository cargoes,
            CancellationRequestRepository cancellations, CargoEventNotifier events, Clock clock) {
        this.cargoes = cargoes;
        this.cancellations = cancellations;
        this.events = events;
        this.clock = clock;
    }

    /** 承認待ちの一覧（US30-4）。**件数の遷移先である**。 */
    public List<CancellationRequest> awaitingDecision() {
        return cancellations.findAwaitingDecision(AWAITING_LIMIT);
    }

    /**
     * その予約のキャンセル申請の<strong>履歴</strong>（新しい順・US30-10）。
     *
     * <p><strong>最新の 1 件では足りない。</strong>却下されて再申請すると、前回の却下理由が
     * 予約詳細から消える——「なぜ一度断られたか」は、次に荷主と話す営業がいちばん必要と
     * する情報である。
     */
    public java.util.List<CancellationRequest> historyFor(String bookingId) {
        return cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .map(cargo -> cancellations.findAllByCargoId(cargo.id()))
                .orElseGet(java.util.List::of);
    }

    /** その予約の最新の申請。画面が「いまどうなっているか」を出すために引く。 */
    public java.util.Optional<CancellationRequest> latestFor(String bookingId) {
        return cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .flatMap(cargo -> cancellations.findLatestByCargoId(cargo.id()));
    }

    /**
     * 承認する（US30-5）。
     *
     * <p><strong>候補外の港での承認は断る</strong>（[ADR-025] 決定 4）。判定は集約が持つ
     * ——ここで旅程を見に行くと、候補の規則が 2 か所に分かれる。
     *
     * @throws IllegalArgumentException 承認待ちの申請が無いとき、または候補外の港のとき
     */
    @Transactional
    public CancellationRequest approve(String bookingId, String dischargeLocationUnLocode,
            String decidedBy, String decisionReason) {
        Cargo cargo = requireCargo(bookingId);
        CancellationRequest awaiting = requireAwaiting(cargo);

        if (!cargo.canDischargeAt(dischargeLocationUnLocode)) {
            throw new IllegalArgumentException(
                    "その港では荷降しできません。候補から選んでください");
        }

        CancellationRequest approved = cancellations.updateDecision(awaiting.approve(
                dischargeLocationUnLocode, decidedBy, decisionReason, clock.instant()));
        Cargo cancelled = cargoes.save(cargo.cancel());

        // **公開追跡が開いている。**知らせないと、荷主は自分が申し入れて承認された
        // キャンセルを画面で否定される（[ADR-025] 決定 3）。**理由は載せない**
        cancelled.trackingNumber().ifPresent(trackingNumber ->
                events.cargoCancelled(new CargoCancelled(trackingNumber.value(),
                        cancelled.bookingId().map(Object::toString).orElse(null),
                        approved.decidedAt().orElse(clock.instant()), clock.instant())));
        return approved;
    }

    /**
     * 却下する（US30-7）。
     *
     * <p><strong>予約は輸送中のまま維持される。</strong>却下は「キャンセルしない」という
     * 決定であり、予約を止める決定ではない。止めてしまうと、貨物は行き先を失ったまま
     * 船に乗り続ける。
     */
    @Transactional
    public CancellationRequest reject(String bookingId, String decidedBy, String decisionReason) {
        CancellationRequest awaiting = requireAwaiting(requireCargo(bookingId));

        return cancellations.updateDecision(
                awaiting.reject(decidedBy, decisionReason, clock.instant()));
    }

    private Cargo requireCargo(String bookingId) {
        return cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "予約が見つかりません: " + bookingId));
    }

    private CancellationRequest requireAwaiting(Cargo cargo) {
        return cancellations.findAwaitingByCargoId(cargo.id())
                .orElseThrow(() -> new IllegalArgumentException(
                        "承認待ちのキャンセル申請が見つかりません"));
    }
}
