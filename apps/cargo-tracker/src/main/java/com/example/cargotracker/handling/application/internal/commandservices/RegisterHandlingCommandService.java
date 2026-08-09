package com.example.cargotracker.handling.application.internal.commandservices;

import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.handling.application.internal.outboundservices.acl.CargoSnapshots;
import com.example.cargotracker.handling.domain.model.CargoBookingId;
import com.example.cargotracker.handling.domain.model.CargoSnapshot;
import com.example.cargotracker.handling.domain.model.CustomsDeclaration;
import com.example.cargotracker.handling.domain.model.HandlingActivity;
import com.example.cargotracker.handling.domain.model.HandlingType;
import com.example.cargotracker.handling.domain.model.HandlingValidation;
import com.example.cargotracker.handling.domain.model.ClaimConfirmation;
import com.example.cargotracker.handling.domain.model.HandledCargo;
import com.example.cargotracker.handling.domain.model.HandlingDetails;
import com.example.cargotracker.handling.domain.model.HandlingVoyageNumber;
import com.example.cargotracker.handling.domain.model.ScannedTrackingNumber;
import com.example.cargotracker.handling.domain.model.RegisterHandlingCommand;
import com.example.cargotracker.handling.domain.repository.CustomsDeclarationRepository;
import com.example.cargotracker.handling.domain.repository.HandlingActivityRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷役作業の登録（US15）。
 *
 * <p><strong>このサービスが書くのは荷役作業だけである。</strong> 追跡の輸送状態も
 * 予約の進行も、{@link HandlingActivityRegisteredEvent} を購読した各 BC が
 * 自分のトランザクションで更新する（ADR-009）。
 *
 * <p><strong>運ぶのは起きた事実であり、命令ではない。</strong> 「輸送状態を進めよ」ではなく
 * 「JPOSA で V001 に積み込んだ」を伝える。どう解釈するかは購読側が決める。
 *
 * <p>結果整合であるため、<strong>登録の直後は追跡にまだ反映されていない瞬間がある</strong>。
 * この時間を短く保つことは運用の関心事であり、業務としては「記録は必ず残る／反映は追って行われる」
 * が正しい形である（荷役は最も頻度が高く、追跡の都合で記録が失敗してはならない）。
 */
@Service
public class RegisterHandlingCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.handling");

    private final HandlingActivityRepository handlingRepository;
    private final CustomsDeclarationRepository declarationRepository;
    private final CargoSnapshots cargoSnapshots;
    private final ApplicationEventPublisher eventPublisher;

    /** 業務のタイムゾーン。**作業日と発行日の比較は業務日で行う**（UTC で判断しない）。 */
    private final java.time.Clock clock;

    public RegisterHandlingCommandService(
            HandlingActivityRepository handlingRepository,
            CustomsDeclarationRepository declarationRepository,
            CargoSnapshots cargoSnapshots,
            ApplicationEventPublisher eventPublisher,
            java.time.Clock clock) {
        this.handlingRepository = handlingRepository;
        this.declarationRepository = declarationRepository;
        this.cargoSnapshots = cargoSnapshots;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 荷役作業を登録する。
     *
     * <p><strong>予定と違っても登録は拒否しない。</strong> 拒否すると、実際に起きた
     * 作業がどこにも残らず、追跡は現実と食い違ったまま進む。伝えるのは警告である。
     */
    @Transactional
    public Result register(Request request) {
        Optional<CargoSnapshots.Snapshot> found =
                cargoSnapshots.findByTrackingNumber(request.trackingNumber());
        if (found.isEmpty()) {
            // 受入基準: 追跡番号が存在しない場合、エラーメッセージが表示される
            return Result.notFound(request.trackingNumber());
        }
        CargoSnapshots.Snapshot snapshot = found.get();

        // **通関済でなければ引取は実行しない**（US29 / ビジネスルール 2）。
        // 誤配も荷受人違いも「起きた事実」として記録するが、
        // **通関前の引き渡しは業務として実行してはならない**。記録の対象ではなく
        // 拒否の対象である。なぜ止まっているのかを言わないと、現場は待つ理由が分からない
        if (request.type() == HandlingType.CLAIM) {
            Optional<CustomsDeclaration> declaration =
                    declarationRepository.findByTrackingNumber(request.trackingNumber());
            if (declaration.isPresent() && !declaration.get().allowsClaim()) {
                return Result.rejected(
                        "通関が完了していないため引取を登録できません。現在の通関状態は「%s」です"
                                .formatted(declaration.get().status().displayName()));
            }
        }

        HandlingActivity activity;
        try {
            activity = HandlingActivity.register(new RegisterHandlingCommand(
                    // **読み取った番号と引き当てた予約はひと組で扱う**
                    new HandledCargo(
                            new ScannedTrackingNumber(request.trackingNumber()),
                            new CargoBookingId(UUID.fromString(snapshot.bookingId()))),
                    // **種別ごとの要否は HandlingDetails が守る。**
                    // 要らない詳細が混ざって届いても捨てる（種別を選び直すたびに
                    // 入力し直させない）
                    HandlingDetails.of(
                            request.type(),
                            request.voyageNumber() == null || request.voyageNumber().isBlank()
                                    ? null : new HandlingVoyageNumber(request.voyageNumber()),
                            claimConfirmationOf(request)),
                    request.completionTime(),
                    Location.of(request.locationUnlocode()),
                    request.note(),
                    request.operatorName()), clock.getZone());
        } catch (IllegalArgumentException e) {
            // 航海番号の欠落など、入力の誤り。**業務のことばで返す**
            return Result.rejected(e.getMessage());
        }

        HandlingValidation validation = activity.isValidFor(toDomain(snapshot));
        handlingRepository.save(activity);

        // **コミットしてから購読側が動く**（AFTER_COMMIT）。ここで発行するのは
        // 「起きた事実」であり、誰が何をするかは知らない
        eventPublisher.publishEvent(new HandlingActivityRegisteredEvent(
                activity.cargoBookingId().value(),
                request.trackingNumber(),
                activity.type().name(),
                activity.completionTime(),
                activity.location().unlocode(),
                activity.voyageNumber() == null ? null : activity.voyageNumber().value(),
                validation.isMisrouted()));

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("荷役登録 trackingNumber={} type={} location={} 判定={} actor={}",
                    AuditValue.sanitize(request.trackingNumber()), activity.type(),
                    activity.location().unlocode(), validation.outcome(),
                    AuditValue.sanitize(request.operatorName()));
        }
        return Result.registered(validation);
    }

    private static CargoSnapshot toDomain(CargoSnapshots.Snapshot snapshot) {
        return new CargoSnapshot(
                snapshot.bookingId(),
                snapshot.origin(),
                snapshot.destination(),
                snapshot.consigneeName(),
                snapshot.legs().stream()
                        .map(leg -> new CargoSnapshot.LegSnapshot(
                                leg.voyageNumber(), leg.loadLocation(), leg.unloadLocation()))
                        .toList());
    }

    /**
     * 引取確認を組み立てる。
     *
     * <p>引取以外では {@code null} を返す。<strong>要否の判断はここでは行わない</strong>
     * （{@link HandlingDetails} が種別に従って捨てる）。ここが行うのは
     * 「入力があれば確認として組み立てる」だけである。
     */
    private static ClaimConfirmation claimConfirmationOf(Request request) {
        if (!request.type().requiresClaimConfirmation()) {
            return null;
        }
        return ClaimConfirmation.byCode(request.confirmationCode(), request.consigneeName());
    }

    /**
     * 荷役の登録要求（画面からの入力）。
     *
     * @param trackingNumber   追跡番号
     * @param type             荷役種別
     * @param completionTime   作業日時
     * @param locationUnlocode 作業場所（UN/LOCODE）
     * @param voyageNumber     航海番号（積込・荷降しでは必須）
     * @param confirmationCode 引取確認コード（引取のみ）
     * @param consigneeName    受け取った人の氏名（引取のみ）
     * @param note             担当者メモ（任意）
     * @param operatorName     作業員名
     */
    public record Request(
            String trackingNumber,
            HandlingType type,
            Instant completionTime,
            String locationUnlocode,
            String voyageNumber,
            String confirmationCode,
            String consigneeName,
            String note,
            String operatorName) {
    }

    /** 登録の結果。 */
    public enum Outcome {
        /** 登録した。 */
        REGISTERED,
        /** 追跡番号が見つからない。 */
        NOT_FOUND,
        /** 入力の誤りで登録できない。 */
        REJECTED
    }

    /**
     * 登録の結果。
     *
     * @param outcome    結果の種別
     * @param validation 妥当性検証の結果。登録できなかった場合は {@code null}
     * @param reason     登録できなかった理由。登録できた場合は {@code null}
     */
    public record Result(Outcome outcome, HandlingValidation validation, String reason) {

        static Result registered(HandlingValidation validation) {
            return new Result(Outcome.REGISTERED, validation, null);
        }

        static Result notFound(String trackingNumber) {
            return new Result(Outcome.NOT_FOUND, null,
                    "追跡番号 %s の貨物が見つかりません".formatted(trackingNumber));
        }

        static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, null, reason);
        }

        public boolean isRegistered() {
            return outcome == Outcome.REGISTERED;
        }
    }
}
