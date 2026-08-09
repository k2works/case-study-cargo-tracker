package com.example.cargotracker.handling.application.internal.commandservices;

import com.example.cargotracker.handling.application.internal.outboundservices.acl.CargoSnapshots;
import com.example.cargotracker.handling.domain.model.CustomsDeclaration;
import com.example.cargotracker.handling.domain.model.CustomsStatus;
import com.example.cargotracker.handling.domain.model.CustomsStatusChange;
import com.example.cargotracker.handling.domain.model.DeclarationNumber;
import com.example.cargotracker.handling.domain.repository.CustomsDeclarationRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.event.CustomsStatusChangedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通関申告の登録と状態更新（US29）。
 *
 * <p><strong>拒否は例外ではなく結果で返す。</strong> 「通関の荷役が無い」も
 * 「理由が空」も業務のエラーであり、500 にすると利用者には障害に見える。
 */
@Service
public class CustomsDeclarationCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.handling");

    /** 結果。 */
    public enum Outcome {
        /** 受け付けた。 */
        ACCEPTED,
        /** 対象が見つからない。 */
        NOT_FOUND,
        /** 業務のルールで受け付けられない。 */
        REJECTED,
        /** 保存で競合した。 */
        CONFLICTED
    }

    /**
     * @param outcome       結果
     * @param reason        受け付けられなかった理由。**そのまま画面に出す**
     * @param declarationId 受け付けたときの申告 ID
     */
    public record Result(Outcome outcome, String reason, Long declarationId) {

        static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, reason, null);
        }
    }

    private final CustomsDeclarationRepository declarationRepository;
    private final CargoSnapshots cargoSnapshots;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CustomsDeclarationCommandService(
            CustomsDeclarationRepository declarationRepository,
            CargoSnapshots cargoSnapshots,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.declarationRepository = declarationRepository;
        this.cargoSnapshots = cargoSnapshots;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 通関申告を登録する（US29）。
     *
     * <p><strong>入力は追跡番号、保存は荷役作業への紐付けである。</strong>
     * 申告は「どの荷役でどの貨物を通したか」の記録であり、貨物に紐づかない申告は
     * 業務上あり得ない。通関の荷役が無ければ<strong>次にすることを示して拒む</strong>。
     */
    @Transactional
    public Result declare(String trackingNumber, String declarationNumber, Instant declaredAt) {
        if (cargoSnapshots.findByTrackingNumber(trackingNumber).isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null, null);
        }
        Optional<Long> handlingId = declarationRepository.findCustomsHandlingId(trackingNumber);
        if (handlingId.isEmpty()) {
            return Result.rejected(
                    "この貨物にはまだ通関の荷役が登録されていません。"
                            + "先に荷役種別「通関」で作業を登録してください");
        }
        if (declarationRepository.findByTrackingNumber(trackingNumber).isPresent()) {
            // **1 貨物に 2 つの申告を作らない。** どちらが引取の可否を決めるのかが
            // 決まらなくなる
            return Result.rejected("この貨物にはすでに通関申告が登録されています");
        }

        CustomsDeclaration declaration;
        try {
            declaration = CustomsDeclaration.declare(
                    new DeclarationNumber(declarationNumber), declaredAt, clock.instant());
        } catch (IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }
        if (!declarationRepository.save(handlingId.get(), declaration)) {
            return new Result(Outcome.CONFLICTED,
                    "別の担当者が先に更新しました。最新の内容を確認してください", null);
        }

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("通関申告の登録 追跡番号={} 申告番号={}",
                    AuditValue.sanitize(trackingNumber), AuditValue.sanitize(declarationNumber));
        }
        return new Result(Outcome.ACCEPTED, null, null);
    }

    /**
     * 通関状態を更新する（US29「更新時は理由の入力が必須」）。
     *
     * <p>結果は<strong>ドメインイベントで伝える</strong>。通関完了の通知を作るのは
     * Booking、税関保留の例外を起票するのは Tracking であり、
     * ここからは呼ばない（ADR-009 / ADR-012）。
     */
    @Transactional
    public Result updateStatus(
            long declarationId, CustomsStatus next, String reason, String actor) {

        Optional<CustomsDeclaration> found = declarationRepository.findById(declarationId);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null, null);
        }
        CustomsDeclaration declaration = found.get();
        String trackingNumber = declarationRepository.findTrackingNumber(declarationId)
                .orElse(null);
        if (trackingNumber == null) {
            return new Result(Outcome.NOT_FOUND, null, null);
        }

        CustomsStatusChange change;
        try {
            change = declaration.updateStatus(next, reason, actor, clock.instant());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }

        Optional<CargoSnapshots.Snapshot> snapshot =
                cargoSnapshots.findByTrackingNumber(trackingNumber);
        Optional<Long> handlingId = declarationRepository.findCustomsHandlingId(trackingNumber);
        if (snapshot.isEmpty() || handlingId.isEmpty()) {
            // **ここだけ例外にしない。** このクラスは冒頭で「拒否は結果で返す」と
            // 宣言している。orElseThrow で 500 にすると、その宣言の唯一の抜け穴になる
            // （しかもこの時点で集約はメモリ上で状態遷移済みである）
            return new Result(Outcome.NOT_FOUND, null, null);
        }
        if (!declarationRepository.save(handlingId.get(), declaration)) {
            return new Result(Outcome.CONFLICTED,
                    "別の担当者が先に更新しました。最新の内容を確認してください", null);
        }

        eventPublisher.publishEvent(new CustomsStatusChangedEvent(
                UUID.fromString(snapshot.get().bookingId()),
                trackingNumber,
                declaration.declarationNumber().value(),
                change.to().displayName(),
                change.to() == CustomsStatus.CLEARED,
                // **不可も対応が要る。** 留置だけを拾うと、積戻し・廃棄・関税の
                // 争いに発展する最も重い状態が、最も静かになる
                change.to().needsAttention(),
                reason,
                change.changedAt(),
                actor));

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("通関状態の更新 申告 ID={} {}→{} 理由あり actor={}",
                    declarationId, change.from().name(), change.to().name(),
                    AuditValue.sanitize(actor));
        }
        return new Result(Outcome.ACCEPTED, null, declarationId);
    }
}
