package com.example.cargotracker.handling.application.internal.commandservices;

import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.handling.application.internal.outboundservices.acl.CargoSnapshots;
import com.example.cargotracker.handling.domain.model.valueobjects.CargoBookingId;
import com.example.cargotracker.handling.domain.model.ClaimCodeMatch;
import com.example.cargotracker.handling.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handling.domain.model.aggregates.CustomsDeclaration;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingActivity;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingValidation;
import com.example.cargotracker.handling.domain.model.valueobjects.ClaimConfirmation;
import com.example.cargotracker.handling.domain.model.valueobjects.HandledCargo;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingDetails;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingVoyageNumber;
import com.example.cargotracker.handling.domain.model.valueobjects.ScannedTrackingNumber;
import com.example.cargotracker.handling.domain.model.commands.RegisterHandlingCommand;
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
            Result rejection = rejectIfCustomsNotCleared(request, toDomain(snapshot));
            if (rejection != null) {
                return rejection;
            }
            // **照合する相手がシステムの中にある**（US35）。IT7 の引取記録は
            // 提示された値をそのまま書き写すだけで、記録はできるが証明にならなかった
            rejection = rejectIfClaimCodeMismatched(request, toDomain(snapshot));
            if (rejection != null) {
                return rejection;
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
            return rejected(request, e.getMessage());
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

    /**
     * 保存せずに予定ルートとの照合だけを行う（US28）。
     *
     * <p>受入基準は「登録<strong>前</strong>に警告が表示される」と書いている。
     * <strong>これは順序の要求であり、文言だけでは満たせない</strong> —
     * 登録したあとに「予定と違いました」と伝えるのでは、作業員は取り消す手段を
     * 持たない（取り消しは US36 であり、まだ無い）。
     *
     * <p><strong>登録と同じ経路で判定する。</strong> 別の判定を書くと、
     * 「警告は出なかったのに登録したら誤配になった」形を作れてしまう。
     *
     * @return 判定。追跡番号が引き当たらなければ空
     */
    @Transactional(readOnly = true)
    public Optional<HandlingValidation> validateOnly(Request request) {
        return cargoSnapshots.findByTrackingNumber(request.trackingNumber())
                .map(snapshot -> HandlingActivity.register(new RegisterHandlingCommand(
                        new HandledCargo(
                                new ScannedTrackingNumber(request.trackingNumber()),
                                new CargoBookingId(UUID.fromString(snapshot.bookingId()))),
                        HandlingDetails.of(
                                request.type(),
                                request.voyageNumber() == null || request.voyageNumber().isBlank()
                                        ? null : new HandlingVoyageNumber(request.voyageNumber()),
                                claimConfirmationOf(request)),
                        request.completionTime(),
                        Location.of(request.locationUnlocode()),
                        request.note(),
                        request.operatorName()), clock.getZone())
                        .isValidFor(toDomain(snapshot)));
    }

    /**
     * 通関が済んでいない引取を拒む（US29 / C29）。
     *
     * <p><strong>「申告があるか」ではなく「通関が要るか」で判断する。</strong>
     * IT11 は申告のある貨物にしか拒否が効かず、<strong>申告を出し忘れた輸入貨物は
     * 引取が通っていた</strong>。実務では出し忘れている貨物こそ引き取らせてはいけない。
     *
     * <p>要否の判断は {@link CargoSnapshot#requiresCustoms()} が持つ。
     * ここで「国が違えば」と書くと、同じ規則が 2 か所に散る。
     *
     * @return 拒む場合の結果。登録してよければ {@code null}
     */
    private Result rejectIfCustomsNotCleared(Request request, CargoSnapshot cargo) {
        Optional<CustomsDeclaration> declaration =
                declarationRepository.findByTrackingNumber(request.trackingNumber());
        if (declaration.isPresent()) {
            return declaration.get().allowsClaim() ? null : rejected(request,
                    "通関が完了していないため引取を登録できません。現在の通関状態は「%s」です"
                            .formatted(declaration.get().status().displayName()));
        }
        if (!cargo.requiresCustoms()) {
            // 同じ国の中で完結する輸送に通関は要らない。**常に拒むと国内輸送が止まる**
            return null;
        }
        return rejected(request,
                "この貨物は通関が必要ですが、通関申告が登録されていません。"
                        + "先に通関の荷役と申告を登録してください");
    }

    /**
     * 拒んだ事実を監査ログに残してから返す（C43）。
     *
     * <p><strong>成功だけを記録すると、何度も試した形跡が残らない。</strong>
     * 総当たりは「1 回の成功」ではなく「多数の失敗」として現れる。
     *
     * <p><strong>入力そのものは書かない。</strong> 荷受人氏名や確認コードを残すと、
     * ログの閲覧権限が実質的に個人情報の閲覧権限になる。残すのは
     * <strong>何を拒んだか</strong>であって、何を入力したかではない。
     */
    private Result rejected(Request request, String reason) {
        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("荷役登録の拒否 trackingNumber={} type={} 理由={} actor={}",
                    AuditValue.sanitize(request.trackingNumber()), request.type(),
                    reason, AuditValue.sanitize(request.operatorName()));
        }
        return Result.rejected(reason);
    }

    /**
     * 引取確認コードが一致しなければ拒む（US35）。
     *
     * <p><strong>追跡番号を知っているだけでは引き取れない。</strong> 追跡番号は
     * 荷主が取引先へ転送する合鍵であり、引き渡しの証明ではない。
     *
     * <p><strong>採番されていない予約では照合しない。</strong> 列が無かったころに
     * 確定した予約はコードを持たない。拒むと<strong>過去の貨物が誰も引き取れなくなる</strong>
     * （不変条件の追加が既存の行を壊す形）。新しく確定する予約は必ず採番される。
     *
     * @return 拒む場合の結果。登録してよければ {@code null}
     */
    private Result rejectIfClaimCodeMismatched(Request request, CargoSnapshot cargo) {
        if (cargo.claimCode() == null || cargo.claimCode().isBlank()) {
            return null;
        }
        if (ClaimCodeMatch.matches(cargo.claimCode(), request.confirmationCode())) {
            return null;
        }
        // **入力された値は理由に含めない。** 監査ログに流れ、
        // ログの閲覧権限が実質的に引取の権限になる
        return rejected(request,
                "引取確認コードが一致しません。荷受人に予約時に伝えたコードを確認してください");
    }

    private static CargoSnapshot toDomain(CargoSnapshots.Snapshot snapshot) {
        return new CargoSnapshot(
                snapshot.bookingId(),
                snapshot.origin(),
                snapshot.destination(),
                snapshot.consigneeName(),
                snapshot.claimCode(),
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
     * @param work           作業そのもの（いつ・どこで・どの便で・誰が）
     * @param claim          引取の確認（引取以外では空）
     */
    public record Request(
            String trackingNumber,
            HandlingType type,
            Work work,
            Claim claim) {

        /**
         * 作業そのもの（いつ・どこで・どの便で・誰が）。
         *
         */
        public record Work(
                Instant completionTime,
                String locationUnlocode,
                String voyageNumber,
                String note,
                String operatorName) { }

        /**
         * 引取の確認（引取以外では両方 {@code null}）。
         *
         */
        public record Claim(String confirmationCode, String consigneeName) { }

        // --- 呼び出し側が使う名前（委譲するアクセサ）---

        /** @return 作業日時 */
        public Instant completionTime() {
            return work.completionTime();
        }

        /** @return 作業場所（UN/LOCODE） */
        public String locationUnlocode() {
            return work.locationUnlocode();
        }

        /** @return 航海番号 */
        public String voyageNumber() {
            return work.voyageNumber();
        }

        /** @return 担当者メモ */
        public String note() {
            return work.note();
        }

        /** @return 作業員名 */
        public String operatorName() {
            return work.operatorName();
        }

        /** @return 引取確認コード */
        public String confirmationCode() {
            return claim.confirmationCode();
        }

        /** @return 実際に受け取った方の氏名 */
        public String consigneeName() {
            return claim.consigneeName();
        }

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
