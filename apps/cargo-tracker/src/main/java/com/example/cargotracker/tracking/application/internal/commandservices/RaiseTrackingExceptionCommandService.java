package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.event.CargoExceptionRaisedEvent;
import com.example.cargotracker.shared.domain.event.CargoExceptionResolvedEvent;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.PortNames;
import com.example.cargotracker.tracking.domain.model.ExceptionOccurrence;
import com.example.cargotracker.tracking.domain.model.ExceptionResolution;
import com.example.cargotracker.tracking.domain.model.ExceptionType;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingExceptionEvent;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 例外の起票と解決（US19 / US20）。
 *
 * <p><strong>拒否は例外ではなく結果で返す。</strong> 「引取が完了している」も
 * 「未解決の例外がある」も業務のエラーであり、500 にすると利用者には障害に見える。
 * IT9 のレビューで、集約の守りが画面から 500 として現れる形を指摘されている。
 */
@Service
public class RaiseTrackingExceptionCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.tracking");

    /** 結果。 */
    public enum Outcome {
        /** 受け付けた。 */
        ACCEPTED,
        /** 追跡番号が見つからない。 */
        NOT_FOUND,
        /** 業務のルールで受け付けられない。 */
        REJECTED,
        /** 楽観的ロックの競合。 */
        CONFLICTED
    }

    /**
     * @param outcome     結果
     * @param reason      受け付けられなかった理由。**そのまま画面に出す**
     * @param exceptionId 受け付けたときの例外 ID
     */
    public record Result(Outcome outcome, String reason, Long exceptionId) {

        static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, reason, null);
        }
    }

    private final TrackingActivityRepository trackingRepository;
    private final PortNames portNames;
    private final ApplicationEventPublisher eventPublisher;

    /** **業務のタイムゾーンで「今」を決める。** UTC で判断すると時差の分だけずれる。 */
    private final Clock clock;

    public RaiseTrackingExceptionCommandService(
            TrackingActivityRepository trackingRepository,
            PortNames portNames,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.trackingRepository = trackingRepository;
        this.portNames = portNames;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 例外を起票する（US19 / US20）。 */
    @Transactional
    public Result raise(
            String trackingNumber, ExceptionType type, String locationUnlocode,
            Instant occurredAt, String description, String actor) {

        if (type == null || !ExceptionType.manuallyRaisable().contains(type)) {
            // **画面から消すだけでは足りない。** リクエストを直接組み立てれば送れてしまう
            return Result.rejected("画面から登録できない例外種別です");
        }

        TrackingNumber number;
        Location location;
        try {
            number = new TrackingNumber(trackingNumber);
            location = Location.of(locationUnlocode);
        } catch (IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }
        if (portNames.findNames(List.of(location.unlocode())).isEmpty()) {
            return Result.rejected("登録されていない港です: " + location.unlocode());
        }

        Optional<TrackingActivity> found = trackingRepository.findByTrackingNumber(number);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null, null);
        }
        TrackingActivity tracking = found.get();

        TrackingExceptionEvent raised;
        try {
            raised = tracking.raiseException(
                    ExceptionOccurrence.raise(type, location, occurredAt, description),
                    clock.instant());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }

        if (!trackingRepository.update(tracking)) {
            return new Result(Outcome.CONFLICTED,
                    "別の担当者が先に更新しました。最新の内容を確認してください", null);
        }

        // **Booking を呼ばない。** 起きた事実だけを伝え、荷主に何と伝えるかは
        // Booking が決める（ADR-009 / ADR-012）
        eventPublisher.publishEvent(new CargoExceptionRaisedEvent(
                tracking.bookingId().value(), number.value(), type.displayName(),
                occurredAt, location.unlocode(), description,
                raised.escalationFlag(), actor));

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("例外の起票 追跡番号={} 種別={} 場所={} エスカレーション={} actor={}",
                    AuditValue.sanitize(number.value()), type.name(),
                    location.unlocode(), raised.escalationFlag(),
                    AuditValue.sanitize(actor));
        }
        return new Result(Outcome.ACCEPTED, null, null);
    }

    /**
     * 他の BC で起きた事実から例外を起票する（US29 の税関保留 / US28 の誤配）。
     *
     * <p><strong>画面から登録できない種別はここから起票する。</strong> {@link #raise} は
     * 「画面から登録できる種別か」を検査するため、税関保留や誤配は通らない。
     * 検査を緩めると<strong>画面のプルダウンに出ていない種別を、リクエストを
     * 組み立てれば登録できてしまう</strong>。入口を分けることで、
     * 手で起票してよい種別の一覧を 1 か所に保てる。
     *
     * <p>発生場所は使わない。留置も誤配も<strong>どこで起きたかより、
     * 何が起きたかが問題である</strong>（場所は元の記録が持っている）。
     */
    @Transactional
    public Result raiseAutomatically(
            String trackingNumber, ExceptionType type, Instant occurredAt,
            String description, String actor) {

        TrackingNumber number;
        try {
            number = new TrackingNumber(trackingNumber);
        } catch (IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }

        Optional<TrackingActivity> found = trackingRepository.findByTrackingNumber(number);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null, null);
        }
        TrackingActivity tracking = found.get();

        TrackingExceptionEvent raised;
        try {
            raised = tracking.raiseException(
                    // **場所を持たない発生状況を作れるのは自動起票だけである。**
                    // 画面からの起票では場所を必須にしている（ExceptionOccurrence.raise）
                    new ExceptionOccurrence(type, null, occurredAt, description),
                    clock.instant());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }

        if (!trackingRepository.update(tracking)) {
            return new Result(Outcome.CONFLICTED,
                    "別の担当者が先に更新しました。最新の内容を確認してください", null);
        }

        eventPublisher.publishEvent(new CargoExceptionRaisedEvent(
                tracking.bookingId().value(), number.value(), type.displayName(),
                occurredAt, null, description, raised.escalationFlag(), actor));

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("例外の自動起票 追跡番号={} 種別={} actor={}",
                    AuditValue.sanitize(number.value()), type.name(),
                    AuditValue.sanitize(actor));
        }
        return new Result(Outcome.ACCEPTED, null, null);
    }

    /**
     * 例外を解決する（US19「対応内容（<strong>新しい到着予定日</strong>・対応方針）を
     * 入力して荷主に対応報告を送信できる」）。
     *
     * @param revisedArrival 新しい到着予定日。<strong>任意</strong>
     *                       （到着予定が変わらない対応もある）
     */
    @Transactional
    public Result resolve(
            String trackingNumber, long exceptionId, String notes,
            LocalDate revisedArrival, String actor) {

        ExceptionResolution resolution;
        try {
            // **空の対応報告を荷主に送らない。** 「対応しました」だけの通知は、
            // 何が起きてどうなったのかを荷主に何も伝えない
            resolution = ExceptionResolution.report(notes, revisedArrival);
        } catch (IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }

        TrackingNumber number;
        try {
            number = new TrackingNumber(trackingNumber);
        } catch (IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }

        Optional<TrackingActivity> found = trackingRepository.findByTrackingNumber(number);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null, null);
        }
        TrackingActivity tracking = found.get();

        TrackingExceptionEvent resolved;
        try {
            resolved = tracking.resolveException(exceptionId, resolution, clock.instant());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }

        if (!trackingRepository.update(tracking)) {
            return new Result(Outcome.CONFLICTED,
                    "別の担当者が先に更新しました。最新の内容を確認してください", null);
        }

        eventPublisher.publishEvent(new CargoExceptionResolvedEvent(
                tracking.bookingId().value(), number.value(),
                resolved.exceptionType().displayName(), resolved.resolvedAt(),
                tracking.transportStatus().displayName(), notes, actor));

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("例外の解決 追跡番号={} 例外 ID={} 復帰状態={} actor={}",
                    AuditValue.sanitize(number.value()), exceptionId,
                    tracking.transportStatus().name(), AuditValue.sanitize(actor));
        }
        return new Result(Outcome.ACCEPTED, null, exceptionId);
    }
}
