package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl
        .CargoCurrentLocation;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.CancellationFeeRate;
import com.example.cargotracker.booking.domain.model.CancellationRequest;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.DischargeCandidates;
import com.example.cargotracker.booking.domain.repository.CancellationRequestRepository;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.domain.event.CargoCancelledEvent;
import com.example.cargotracker.shared.domain.model.Location;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送中の予約キャンセルの申請と承認（US30。遷移表 #10）。
 *
 * <p><strong>申請しても予約は輸送中のままである。</strong> 承認されるまで
 * キャンセルは確定しない — 貨物は船の上にあり、降ろす場所が決まるまで
 * 運び続けるほうが安全である。
 *
 * <p><strong>キャンセル料の算定は Billing に任せる</strong>（ADR-021）。
 * 承認の結果は「キャンセルされた」であり、<strong>金額をその場で画面に出す
 * 必要が無い</strong>。
 */
@Service
public class CancelBookingApprovalCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.booking");

    private final CargoRepository cargoRepository;
    private final CancellationRequestRepository requestRepository;
    private final CargoCurrentLocation currentLocation;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CancelBookingApprovalCommandService(
            CargoRepository cargoRepository,
            CancellationRequestRepository requestRepository,
            CargoCurrentLocation currentLocation,
            ApplicationEventPublisher events,
            Clock clock) {
        this.cargoRepository = cargoRepository;
        this.requestRepository = requestRepository;
        this.currentLocation = currentLocation;
        this.events = events;
        this.clock = clock;
    }

    /** 結果。 */
    public enum Outcome {
        /** 受け付けた。 */
        SUCCEEDED,
        /** 対象の予約・申請が見つからない。 */
        NOT_FOUND,
        /** 業務の条件を満たさない。 */
        REJECTED,
        /** 他の担当者が先に決めた。 */
        CONFLICTED
    }

    /**
     * 結果。
     *
     * @param reason できなかった理由。<strong>そのまま画面に出す</strong>
     */
    public record Result(Outcome outcome, String reason) {

        static Result ok() {
            return new Result(Outcome.SUCCEEDED, null);
        }

        static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, reason);
        }
    }

    /**
     * キャンセルを申請する（US30 の受入基準 1・2）。
     *
     * <p><strong>輸送中だけである。</strong> 輸送開始前は申請を挟まず即座に
     * キャンセルできる（遷移表 #9）。<strong>引取が済んだ後は申請もできない</strong> —
     * 引き渡し済み貨物の取り消しは返送であり別業務である。
     */
    @Transactional
    public Result request(String bookingId, String reason, String actor) {
        Optional<Cargo> found = findCargo(bookingId);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        Cargo cargo = found.get();
        if (!cargo.bookingStatus().requiresCancelApproval()) {
            return Result.rejected(
                    "%s の予約はこの手続きでキャンセルできません"
                            .formatted(cargo.bookingStatus().displayName()));
        }
        BookingId id = cargo.bookingId();
        // **業務の言葉で拒む。** 部分ユニーク索引でも防いでいるが、
        // 制約に頼ると画面には 500 が出る（ローカルの H2 では索引が働かない）
        if (requestRepository.existsPendingFor(id)) {
            return Result.rejected("この予約はすでにキャンセルの承認待ちです");
        }

        CancellationRequest request;
        try {
            request = CancellationRequest.request(
                    id, reason, CancellationFeeRate.of(cargo.bookingStatus()),
                    actor, clock.instant());
        } catch (IllegalArgumentException e) {
            return Result.rejected(e.getMessage());
        }
        requestRepository.save(request);
        AUDIT.info("キャンセルを申請しました bookingId={} actor={}", bookingId, actor);
        return Result.ok();
    }

    /**
     * 承認する（US30 の受入基準 3・4）。
     *
     * <p><strong>陸揚げ地は候補の中からしか選べない。</strong> 候補は
     * 現在地の港と、まだ着いていない寄港地である。
     *
     * <p><strong>承認の時点でまだ輸送中かを確かめる。</strong> 申請してから
     * 承認までに引取が済むことがある。<strong>引き渡し済みの貨物を
     * キャンセルすると返送の業務になる。</strong>
     */
    @Transactional
    public Result approve(long requestId, String dischargeUnlocode, String actor) {
        Optional<CancellationRequest> found = requestRepository.findById(requestId);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        CancellationRequest request = found.get();
        Optional<Cargo> loaded = cargoRepository.findById(request.bookingId());
        if (loaded.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        Cargo cargo = loaded.get();
        if (!cargo.bookingStatus().requiresCancelApproval()) {
            return Result.rejected(
                    "この貨物は%sです。いまはキャンセルできません"
                            .formatted(cargo.bookingStatus().displayName()));
        }

        try {
            request.approve(
                    Location.of(dischargeUnlocode), candidatesFor(cargo), actor,
                    clock.instant());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.rejected(e.getMessage());
        }
        if (!requestRepository.update(request)) {
            return new Result(Outcome.CONFLICTED, null);
        }

        cargo.approveCancel();
        if (!cargoRepository.update(cargo)) {
            // **黙って進まない。** 予約が輸送中のまま「承認済み」の申請が残ると、
            // 荷役は運び続け、経理はキャンセル料を請求する
            return new Result(Outcome.CONFLICTED, null);
        }

        // **キャンセル料の算定はイベントで伝える**（ADR-021）。
        // 承認画面の前にいる追跡管理者は、金額について何もできない
        events.publishEvent(new CargoCancelledEvent(
                request.bookingId().value(), request.feeRate().value(),
                dischargeUnlocode, actor, clock.instant()));
        AUDIT.info("キャンセルを承認しました bookingId={} discharge={} actor={}",
                request.bookingId().value(), dischargeUnlocode, actor);
        return Result.ok();
    }

    /**
     * 却下する（US30 の受入基準 5）。
     *
     * <p><strong>輸送中のまま維持される。</strong> 却下しても記録は残る —
     * 却下したことも経緯である。
     */
    @Transactional
    public Result reject(long requestId, String reason, String actor) {
        Optional<CancellationRequest> found = requestRepository.findById(requestId);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        CancellationRequest request = found.get();
        try {
            request.reject(actor, clock.instant(), reason);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.rejected(e.getMessage());
        }
        if (!requestRepository.update(request)) {
            return new Result(Outcome.CONFLICTED, null);
        }
        AUDIT.info("キャンセルを却下しました bookingId={} actor={}",
                request.bookingId().value(), actor);
        return Result.ok();
    }

    /**
     * 陸揚げ地の候補（<strong>戻り値をそのまま画面に出す</strong>）。
     *
     * <p>現在地が読めなくても候補を空にしない。追跡の記録がまだ無い貨物はある。
     */
    public List<Location> candidatesFor(Cargo cargo) {
        String trackingNumber = cargo.trackingNumber() == null
                ? null : cargo.trackingNumber().value();
        return DischargeCandidates.of(
                currentLocation.findByTrackingNumber(trackingNumber).orElse(null),
                cargo.cargoItinerary(), clock.instant());
    }

    /** 予約を引く（<strong>形式の違う ID を例外にしない</strong>）。 */
    public Optional<Cargo> findCargo(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return Optional.empty();
        }
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(bookingId.strip());
        } catch (IllegalArgumentException e) {
            // **形式の違う ID を例外にしない。** 画面が 500 になる。
            // **catch を読み出しまで広げない** — 復元が投げた例外を
            // 「見つからない」と言い換えると、原因がどこにも残らない
            return Optional.empty();
        }
        return cargoRepository.findById(new BookingId(id));
    }
}
