package com.example.cargotracker.handling.application.internal.commandservices;

import com.example.cargotracker.handling.application.internal.outboundservices.acl.CargoSnapshots;
import com.example.cargotracker.handling.domain.model.aggregates.CorrectionRequest;
import com.example.cargotracker.handling.domain.model.valueobjects.CorrectionRequestType;
import com.example.cargotracker.handling.domain.repository.CorrectionRequestRepository;
import com.example.cargotracker.handling.domain.repository.HandlingActivityRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.event.ClaimCancelledEvent;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 引取記録の訂正・取り消し（US36）。
 *
 * <p>引取は輸送の終点であり、<strong>誤登録をそのままにすると貨物が届いていないのに
 * 配送完了として扱われる</strong>。
 *
 * <p><strong>申請と承認を分ける。</strong> 現場が自分で取り消せると、
 * 引き渡しの証明（US35）が現場の判断で消せることになる。
 */
@Service
public class CorrectionCommandService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.handling");

    private final CorrectionRequestRepository requestRepository;
    private final HandlingActivityRepository handlingRepository;
    private final CargoSnapshots cargoSnapshots;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CorrectionCommandService(
            CorrectionRequestRepository requestRepository,
            HandlingActivityRepository handlingRepository,
            CargoSnapshots cargoSnapshots,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.requestRepository = requestRepository;
        this.handlingRepository = handlingRepository;
        this.cargoSnapshots = cargoSnapshots;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 訂正・取り消しを申請する（荷役作業員）。
     *
     * <p><strong>精算済みの予約には申請できない。</strong> 精算は請求と入金を伴い、
     * 取り消しは返金の業務になる。<strong>ここで通すと、業務として実行できない
     * 承認待ちが待ち行列に残り続ける。</strong>
     *
     * <p><strong>訂正は「直す中身」を伴って初めて申請になる。</strong> 種別だけを
     * 選べて中身を入れられないと、承認しても何も起きない申請が待ち行列に並ぶ。
     *
     * <p><strong>引数を減らすための委譲を作らない。</strong> {@code @Transactional} の
     * メソッドを {@code this} 経由で呼ぶとプロキシを通らず、トランザクションの
     * 境界が意図どおりにならない（SonarQube の指摘）。
     *
     * @param completionTime 訂正後の作業日時。取り消し・変えないなら {@code null}
     * @param note           訂正後のメモ。取り消し・変えないなら {@code null}
     */
    @Transactional
    public Result request(
            long handlingActivityId, CorrectionRequestType type, String reason,
            String requestedBy, java.time.Instant completionTime, String note) {

        Optional<HandlingActivityRepository.CancellableHandling> found =
                handlingRepository.findCancellable(handlingActivityId);
        if (found.isEmpty()) {
            return Result.notFound();
        }
        HandlingActivityRepository.CancellableHandling activity = found.get();
        if (activity.cancelled()) {
            return Result.rejected("この荷役はすでに取り消されています");
        }
        if (isSettled(activity)) {
            return Result.rejected(
                    "精算済みの予約は訂正・取り消しできません。返金を伴うため経理へご連絡ください");
        }
        if (hasPending(handlingActivityId)) {
            return Result.rejected(
                    "この荷役にはすでに承認待ちの申請があります。決定を待ってください");
        }

        CorrectionRequest request;
        try {
            request = CorrectionRequest.request(
                    handlingActivityId, type, reason, requestedBy, clock.instant());
            if (type == CorrectionRequestType.CORRECT) {
                // **訂正は直す中身を伴う。** 中身の無い訂正は、承認しても何も起きない
                request = request.correcting(completionTime, note);
            }
            if (completionTime != null && completionTime.isAfter(clock.instant())) {
                // **荷役は起きた事実である**（C36 と同じ向き）。
                // 訂正の経路から未来の作業日時を入れられては、検査の意味が無い
                return Result.rejected(
                        "作業日時に未来の日時は指定できません。まだ起きていない作業は記録できません");
            }
        } catch (IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }
        long id = requestRepository.save(request);

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("荷役の{}を申請 荷役 ID={} 申請 ID={} actor={}",
                    type.displayName(), handlingActivityId, id,
                    AuditValue.sanitize(requestedBy));
        }
        return Result.accepted(id);
    }

    /**
     * 承認する（追跡管理者）。
     *
     * <p><strong>取り消しの承認だけが貨物の状態を戻す。</strong> 状態を戻すのは
     * 購読側であり、ここからは他 BC を呼ばない（ADR-009 / ADR-012）。
     */
    @Transactional
    public Result approve(long requestId, String approver) {
        Optional<CorrectionRequest> found = requestRepository.findById(requestId);
        if (found.isEmpty()) {
            return Result.notFound();
        }
        CorrectionRequest request = found.get();
        try {
            request.approve(approver, clock.instant());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.rejected(e.getMessage());
        }

        Optional<HandlingActivityRepository.CancellableHandling> target =
                handlingRepository.findCancellable(request.handlingActivityId());
        if (target.isEmpty()) {
            return Result.notFound();
        }

        // **記録を直せてから承認済みにする。** 逆にすると、直っていないのに
        // 「承認済み」と表示され、申請した現場は直ったと思い込む。
        // すでに取り消された荷役には書けない（{@code cancelled_at IS NULL} の条件）
        boolean applied = request.type().revertsCargoStatus()
                ? handlingRepository.markCancelled(
                        request.handlingActivityId(), clock.instant(), approver)
                : handlingRepository.applyCorrection(
                        request.handlingActivityId(),
                        request.details().correctedCompletionTime(),
                        request.details().correctedNote());
        if (!applied) {
            return Result.rejected(
                    "この荷役はすでに取り消されています。決定は記録していません");
        }

        if (!requestRepository.update(request)) {
            // **楽観的ロックで負けた。** 上の書き込みは同じトランザクションにあり、
            // 例外を投げて巻き戻す（決定だけが落ちた状態を残さない）
            throw new java.util.ConcurrentModificationException(
                    "別の担当者が先に決定しました。最新の内容を確認してください");
        }

        if (request.type().revertsCargoStatus()) {
            // **運ぶのは起きた事実である**（ADR-009）。実際に取り消せたときだけ出す
            eventPublisher.publishEvent(new ClaimCancelledEvent(
                    target.get().bookingId(),
                    target.get().trackingNumber(),
                    request.reason(),
                    approver,
                    clock.instant()));
        }

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("荷役の{}を承認 申請 ID={} 荷役 ID={} actor={}",
                    request.type().displayName(), requestId, request.handlingActivityId(),
                    AuditValue.sanitize(approver));
        }
        return Result.accepted(requestId);
    }

    /** 却下する（追跡管理者）。<strong>理由を残す。</strong> */
    @Transactional
    public Result reject(long requestId, String approver, String reason) {
        Optional<CorrectionRequest> found = requestRepository.findById(requestId);
        if (found.isEmpty()) {
            return Result.notFound();
        }
        CorrectionRequest request = found.get();
        try {
            request.reject(approver, clock.instant(), reason);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.rejected(e.getMessage());
        }
        if (!requestRepository.update(request)) {
            throw new java.util.ConcurrentModificationException(
                    "別の担当者が先に決定しました。最新の内容を確認してください");
        }
        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("荷役の{}を却下 申請 ID={} actor={}",
                    request.type().displayName(), requestId, AuditValue.sanitize(approver));
        }
        return Result.accepted(requestId);
    }

    /** 精算済みか。**予約の状態は Booking が持つ**（ACL ポート越しに読む）。 */
    private boolean isSettled(HandlingActivityRepository.CancellableHandling activity) {
        return cargoSnapshots.findByTrackingNumber(activity.trackingNumber())
                .map(snapshot -> "SETTLED".equals(snapshot.bookingStatus()))
                .orElse(false);
    }

    private boolean hasPending(long handlingActivityId) {
        return requestRepository.findByHandlingActivityId(handlingActivityId).stream()
                .anyMatch(request -> request.status().isPending());
    }

    /**
     * 申請・決定の結果。
     *
     * @param outcome   結果の種別
     * @param requestId 申請 ID。受け付けなかった場合は 0
     * @param reason    受け付けなかった理由
     */
    public record Result(Outcome outcome, long requestId, String reason) {

        /** 結果の種別。 */
        public enum Outcome {
            /** 受け付けた。 */
            ACCEPTED,
            /** 対象が無い。 */
            NOT_FOUND,
            /** 業務として実行できない。 */
            REJECTED
        }

        static Result accepted(long requestId) {
            return new Result(Outcome.ACCEPTED, requestId, null);
        }

        static Result notFound() {
            return new Result(Outcome.NOT_FOUND, 0L, null);
        }

        static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, 0L, reason);
        }
    }
}
