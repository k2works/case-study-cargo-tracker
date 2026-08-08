package com.example.cargotracker.tracking.handling.application.internal.commandservices;

import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.tracking.domain.model.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.TrackingVoyageNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import com.example.cargotracker.tracking.handling.application.internal.outboundservices.acl.CargoSnapshots;
import com.example.cargotracker.tracking.handling.application.internal.outboundservices.acl.HandlingProgressPort;
import com.example.cargotracker.tracking.handling.domain.model.CargoBookingId;
import com.example.cargotracker.tracking.handling.domain.model.CargoSnapshot;
import com.example.cargotracker.tracking.handling.domain.model.HandlingActivity;
import com.example.cargotracker.tracking.handling.domain.model.HandlingType;
import com.example.cargotracker.tracking.handling.domain.model.HandlingValidation;
import com.example.cargotracker.tracking.handling.domain.model.HandlingVoyageNumber;
import com.example.cargotracker.tracking.handling.domain.model.RegisterHandlingCommand;
import com.example.cargotracker.tracking.handling.domain.repository.HandlingActivityRepository;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷役作業の登録（US15）。
 *
 * <p>荷役の登録は<strong>3 つのことを 1 つのトランザクションで行う</strong>。
 *
 * <ol>
 *   <li>荷役作業の記録（Handling）</li>
 *   <li>輸送状態の更新（Tracking）</li>
 *   <li>予約の輸送開始・誤配の反映（Booking。ACL 経由）</li>
 * </ol>
 *
 * <p>片方だけ成功すると、<strong>作業は記録されたのに追跡には現れない</strong>という、
 * 現場から見て最も分かりにくい状態になる。
 */
@Service
public class RegisterHandlingCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.handling");

    private final HandlingActivityRepository handlingRepository;
    private final TrackingActivityRepository trackingRepository;
    private final CargoSnapshots cargoSnapshots;
    private final HandlingProgressPort handlingProgress;

    public RegisterHandlingCommandService(
            HandlingActivityRepository handlingRepository,
            TrackingActivityRepository trackingRepository,
            CargoSnapshots cargoSnapshots,
            HandlingProgressPort handlingProgress) {
        this.handlingRepository = handlingRepository;
        this.trackingRepository = trackingRepository;
        this.cargoSnapshots = cargoSnapshots;
        this.handlingProgress = handlingProgress;
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

        HandlingActivity activity;
        try {
            activity = HandlingActivity.register(new RegisterHandlingCommand(
                    new CargoBookingId(UUID.fromString(snapshot.bookingId())),
                    request.type(),
                    request.completionTime(),
                    Location.of(request.locationUnlocode()),
                    request.voyageNumber() == null || request.voyageNumber().isBlank()
                            ? null : new HandlingVoyageNumber(request.voyageNumber()),
                    request.operatorName()));
        } catch (IllegalArgumentException e) {
            // 航海番号の欠落など、入力の誤り。**業務のことばで返す**
            return Result.rejected(e.getMessage());
        }

        HandlingValidation validation = activity.isValidFor(toDomain(snapshot));
        handlingRepository.save(activity);
        recordOnTracking(request, activity);

        // **誤配が確定したときだけ、予約の経路状態を MISROUTED にする**
        // （荷役ビジネスルール 1）。警告に留まる場合は経路そのものは正しい
        if (validation.isMisrouted()) {
            handlingProgress.markMisrouted(activity.cargoBookingId().value());
        }
        // 最初の積込で輸送が始まる（遷移表 #6）。**すでに輸送中なら何もしない**
        if (activity.type() == HandlingType.LOAD) {
            handlingProgress.startTransportIfNotStarted(activity.cargoBookingId().value());
        }

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("荷役登録 trackingNumber={} type={} location={} 判定={} actor={}",
                    AuditValue.sanitize(request.trackingNumber()), activity.type(),
                    activity.location().unlocode(), validation.outcome(),
                    AuditValue.sanitize(request.operatorName()));
        }
        return Result.registered(validation);
    }

    /**
     * 追跡に記録する。
     *
     * <p>追跡レコードは追跡番号の発行時に作られている。<strong>無ければ荷役だけを
     * 残す。</strong> 追跡が無いことを理由に作業の記録まで失うほうが損失が大きい。
     */
    private void recordOnTracking(Request request, HandlingActivity activity) {
        trackingRepository.findByTrackingNumber(new TrackingNumber(request.trackingNumber()))
                .ifPresent(tracking -> {
                    tracking.recordEvent(new TrackingActivityEvent(
                            TrackingEventType.valueOf(activity.type().name()),
                            activity.completionTime(),
                            activity.location(),
                            activity.voyageNumber() == null
                                    ? null
                                    : new TrackingVoyageNumber(
                                            activity.voyageNumber().value())));
                    // **衝突の合図を捨てない。** update が false のときは
                    // 輸送状態もイベントも書かれていない。捨てると
                    // 「積込を登録しました」と出たまま追跡だけが取り残される
                    if (!trackingRepository.update(tracking)) {
                        throw new ConcurrentModificationException(
                                "他の操作が先に行われました。最新の内容を確認してください");
                    }
                });
    }

    private static CargoSnapshot toDomain(CargoSnapshots.Snapshot snapshot) {
        return new CargoSnapshot(
                snapshot.bookingId(),
                snapshot.origin(),
                snapshot.destination(),
                snapshot.legs().stream()
                        .map(leg -> new CargoSnapshot.LegSnapshot(
                                leg.voyageNumber(), leg.loadLocation(), leg.unloadLocation()))
                        .toList());
    }

    /**
     * 荷役の登録要求（画面からの入力）。
     *
     * @param trackingNumber 追跡番号
     * @param type           荷役種別
     * @param completionTime 作業日時
     * @param locationUnlocode 作業場所（UN/LOCODE）
     * @param voyageNumber   航海番号（積込・荷降しでは必須）
     * @param operatorName   作業員名
     */
    public record Request(
            String trackingNumber,
            HandlingType type,
            Instant completionTime,
            String locationUnlocode,
            String voyageNumber,
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
