package com.example.cargotracker.routing.application.internal.commandservices;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.CargoRouteAssignments;
import com.example.cargotracker.routing.domain.model.aggregates.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.entities.ProposedRoute;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.BookingRouteProposalRepository;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 経路の選択・確定と予約への紐付け（US09 / US11）。 */
@Service
public class SelectRouteCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.routing");

    private final BookingRouteProposalRepository proposalRepository;
    private final VoyageRepository voyageRepository;
    private final CargoRouteAssignments cargoRouteAssignments;

    public SelectRouteCommandService(
            BookingRouteProposalRepository proposalRepository,
            VoyageRepository voyageRepository,
            CargoRouteAssignments cargoRouteAssignments) {
        this.proposalRepository = proposalRepository;
        this.voyageRepository = voyageRepository;
        this.cargoRouteAssignments = cargoRouteAssignments;
    }

    /**
     * 候補を 1 件選んで確定し、予約に紐付ける。
     *
     * <p><strong>提案の確定と貨物への反映を 1 つのトランザクションで行う。</strong>
     * 片方だけ成功すると「提案は確定済みなのに貨物は未割り当て」という、
     * 業務上あり得ない状態になる。
     *
     * @param bookingId    予約 ID
     * @param voyageNumber 選ぶ候補の航海番号
     * @param actor        操作した利用者
     */
    @Transactional
    public Result select(RoutingBookingId bookingId, VoyageNumber voyageNumber, String actor) {
        Optional<BookingRouteProposal> found = proposalRepository.findByBookingId(bookingId);
        if (found.isEmpty()) {
            return Result.notFound();
        }

        BookingRouteProposal selected;
        try {
            selected = found.get().select(voyageNumber);
        } catch (IllegalArgumentException e) {
            // 選べない候補・候補にない航海。**業務のことばで返す**
            return Result.rejected(e.getMessage());
        }

        List<CargoRouteAssignments.LegAssignment> legs = toLegs(selected.selectedRoute());
        if (legs.isEmpty()) {
            return Result.rejected("選んだ航海の運送区間が見つかりません");
        }

        var assignment = cargoRouteAssignments.assign(bookingId.value(), legs);
        switch (assignment) {
            case NOT_FOUND -> {
                return Result.notFound();
            }
            case REJECTED -> {
                return Result.rejected("この予約に経路を割り当てられません");
            }
            case CONFLICTED -> {
                return Result.conflicted();
            }
            default -> { /* 割り当てられたので下へ進む */ }
        }

        proposalRepository.save(selected);

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("経路確定 bookingId={} voyageNumber={} 区間={} actor={}",
                    bookingId.value(), voyageNumber.value(), legs.size(),
                    AuditValue.sanitize(actor));
        }
        return Result.assigned(selected);
    }

    /**
     * 選んだ候補を、貨物に割り当てる区間へ翻訳する。
     *
     * <p><strong>航海の実際の区間から作る。</strong> 候補が持つのは端点と経由港だけで
     * あり、区間ごとの発着時刻は航海が持っている。候補の情報だけで組み立てると、
     * <strong>乗り継ぎの時刻が失われた旅程</strong>になる。
     *
     * <p><strong>区間は添字で絞る</strong>（レビュー L1）。以前は乗船時刻から下船時刻
     * までの範囲で絞っていたが、それは<strong>探索が決めた位置を時刻から逆算する</strong>
     * ことであり、同じ港を 2 度通る航海ではどの周回かが時刻に委ねられていた。
     * 探索が選んだ位置（{@link ProposedRoute.LegRange}）をそのまま使う。
     *
     * <p>航海の区間が候補の算出後に減っている場合（区間の添字が範囲外）は
     * 空を返す。<strong>足りない区間を黙って詰めて旅程にしない。</strong>
     */
    private List<CargoRouteAssignments.LegAssignment> toLegs(ProposedRoute route) {
        return voyageRepository.findByVoyageNumber(route.voyageNumber())
                .map(voyage -> {
                    var movements = voyage.schedule().carrierMovements();
                    var range = route.legRange();
                    if (range.landingIndex() >= movements.size()) {
                        return List.<CargoRouteAssignments.LegAssignment>of();
                    }
                    return movements
                            .subList(range.boardingIndex(), range.landingIndex() + 1).stream()
                            .map(m -> new CargoRouteAssignments.LegAssignment(
                                    route.voyageNumber().value(),
                                    m.departureLocation().unlocode(),
                                    m.arrivalLocation().unlocode(),
                                    m.departureTime(),
                                    m.arrivalTime()))
                            .toList();
                })
                .orElseGet(List::of);
    }

    /** 確定の結果。 */
    public enum Outcome {
        /** 確定して予約に紐付けた。 */
        ASSIGNED,
        /** 経路提案が見つからない。 */
        NOT_FOUND,
        /** 選べない候補を指した。 */
        REJECTED,
        /** 別の担当者が先に更新していた。 */
        CONFLICTED
    }

    /**
     * 確定の結果。
     *
     * @param outcome  結果の種別
     * @param proposal 確定した提案。失敗時は {@code null}
     * @param reason   選べなかった理由。該当しない場合は {@code null}
     */
    public record Result(Outcome outcome, BookingRouteProposal proposal, String reason) {

        static Result assigned(BookingRouteProposal proposal) {
            return new Result(Outcome.ASSIGNED, proposal, null);
        }

        static Result notFound() {
            return new Result(Outcome.NOT_FOUND, null, null);
        }

        static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, null, reason);
        }

        static Result conflicted() {
            return new Result(Outcome.CONFLICTED, null, null);
        }

        public boolean isAssigned() {
            return outcome == Outcome.ASSIGNED;
        }
    }
}
