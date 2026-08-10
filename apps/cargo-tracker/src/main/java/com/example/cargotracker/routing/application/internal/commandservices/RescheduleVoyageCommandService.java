package com.example.cargotracker.routing.application.internal.commandservices;

import java.time.Clock;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.AffectedBookings;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.KnownPorts;
import com.example.cargotracker.routing.domain.model.CarrierMovement;
import com.example.cargotracker.routing.domain.model.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.ScheduleChange;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.event.VoyageRescheduledEvent;
import com.example.cargotracker.shared.domain.model.Location;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 航海スケジュール更新のユースケース（US25）。
 *
 * <p><strong>差分の算出と確定を分ける。</strong> 運航変更は登録済みの内容を
 * 上書きするため、何がどう変わるのかを見てから確定できなければならない。
 *
 * <p><strong>確定済み経路の作り直しはしない。</strong> スケジュールが変わった便を
 * 使う予約の経路をここで自動的に組み替えると、利用者の知らないうちに経路が変わる。
 * 再設計は US28（誤配検知と再設計）の領分である。
 */
@Service
public class RescheduleVoyageCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.routing");

    private final VoyageRepository repository;
    private final KnownPorts knownPorts;

    /** 影響する予約の件数を数える（US25 の「気づく手段」）。 */
    private final AffectedBookings affectedBookings;

    /** **出港済みの区間かどうかは業務のタイムゾーンで判断する。** */
    private final Clock clock;

    /**
     * 変更を他 BC に知らせる（C3）。
     *
     * <p><strong>Routing から他 BC を呼ばない</strong>（ADR-012）。運ぶのは起きた事実であり、
     * どう解釈するかは購読側が決める。
     */
    private final ApplicationEventPublisher eventPublisher;

    public RescheduleVoyageCommandService(
            VoyageRepository repository,
            KnownPorts knownPorts,
            AffectedBookings affectedBookings,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.knownPorts = knownPorts;
        this.affectedBookings = affectedBookings;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 変更内容（差分）を求める。**この時点では保存しない。**
     *
     * @return 対象の航海が無ければ空
     */
    @Transactional(readOnly = true)
    public Optional<Preview> preview(RegisterVoyageCommand command) {
        return repository.findByVoyageNumber(command.voyageNumber())
                .map(before -> new Preview(
                        before.changesTo(before.reschedule(command, clock.instant())),
                        affectedBookings.findByVoyageNumber(command.voyageNumber().value())));
    }

    /**
     * 確定する前に見せる内容。
     *
     * @param change            変わった項目
     * @param affectedBookings  この便を経路に含む予約。**空なら連絡の必要は無い**
     */
    public record Preview(
            ScheduleChange change,
            List<AffectedBookings.AffectedBooking> affectedBookings) {

        public Preview {
            affectedBookings = List.copyOf(affectedBookings);
        }
    }

    /** 運航変更を確定する。 */
    @Transactional
    public Result confirm(RegisterVoyageCommand command, String actor) {
        Optional<Voyage> found = repository.findByVoyageNumber(command.voyageNumber());
        if (found.isEmpty()) {
            return Result.notFound();
        }

        // **外部キー違反を 500 にしない**（登録と同じ扱い）
        List<Location> unknown = knownPorts.findUnknown(collectLocations(command));
        if (!unknown.isEmpty()) {
            return Result.unknownPorts(unknown);
        }

        Voyage before = found.get();
        Voyage updated = before.reschedule(command, clock.instant());
        if (!repository.update(updated)) {
            // 他の更新が先行した。**黙って上書きしない**
            return Result.conflicted();
        }

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("航海スケジュール更新 voyageNumber={} changes={} actor={}",
                    AuditValue.sanitize(updated.voyageNumber().value()),
                    before.changesTo(updated).items().size(),
                    AuditValue.sanitize(actor));
        }
        // **起きた事実を知らせる**（C3。ADR-009）。Booking が区間の「いまの日程」を写し、
        // 予約詳細は Routing のテーブルを JOIN せずに印を出せるようになる。
        // **変わった区間だけでなく全区間を運ぶ** — 購読側が変わらなかった区間も
        // 同じ値で写せば、写しと実際がずれない
        eventPublisher.publishEvent(new VoyageRescheduledEvent(
                updated.voyageNumber().value(),
                updated.schedule().carrierMovements().stream()
                        .map(m -> new VoyageRescheduledEvent.MovementSchedule(
                                m.departureLocation().unlocode(),
                                m.arrivalLocation().unlocode(),
                                m.departureTime(),
                                m.arrivalTime()))
                        .toList(),
                clock.instant()));
        return Result.updated(updated);
    }

    private static Set<Location> collectLocations(RegisterVoyageCommand command) {
        Set<Location> locations = new LinkedHashSet<>();
        for (CarrierMovement movement : command.schedule().carrierMovements()) {
            locations.add(movement.departureLocation());
            locations.add(movement.arrivalLocation());
        }
        return locations;
    }

    /**
     * この便を経路に含む生きている予約の件数（C7）。
     *
     * <p><strong>更新のあとにも数える。</strong> 確認画面に出した件数が
     * 確定した瞬間に消えると、次にすべき連絡の量が分からなくなる。
     */
    public int countAffectedBookings(String voyageNumber) {
        return affectedBookings.countByVoyageNumber(voyageNumber);
    }

    /** 更新の結果。 */
    public enum Outcome {
        /** 更新した。 */
        UPDATED,
        /** 指定した航海が無い。 */
        NOT_FOUND,
        /** 港マスタに無い港が含まれている。 */
        UNKNOWN_PORTS,
        /** 他の更新が先行した。 */
        CONFLICTED
    }

    /**
     * 更新の結果。
     *
     * @param outcome      結果の種別
     * @param voyage       更新後の航海。失敗時は {@code null}
     * @param unknownPorts 港マスタに無かった港
     */
    public record Result(Outcome outcome, Voyage voyage, List<Location> unknownPorts) {

        public Result {
            unknownPorts = List.copyOf(unknownPorts);
        }

        static Result updated(Voyage voyage) {
            return new Result(Outcome.UPDATED, voyage, List.of());
        }

        static Result notFound() {
            return new Result(Outcome.NOT_FOUND, null, List.of());
        }

        static Result conflicted() {
            return new Result(Outcome.CONFLICTED, null, List.of());
        }

        static Result unknownPorts(List<Location> ports) {
            return new Result(Outcome.UNKNOWN_PORTS, null, ports);
        }

        public boolean isUpdated() {
            return outcome == Outcome.UPDATED;
        }
    }

    /** 航海番号の型を外に漏らさないための小さな入口。 */
    public Optional<Voyage> find(VoyageNumber voyageNumber) {
        return repository.findByVoyageNumber(voyageNumber);
    }
}
