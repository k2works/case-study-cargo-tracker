package com.example.cargotracker.routing.infrastructure.repositories;

import com.example.cargotracker.routing.domain.model.aggregates.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.valueobjects.Money;
import com.example.cargotracker.routing.domain.model.entities.ProposedRoute;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCriteria;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingWeight;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.BookingRouteProposalRepository;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link BookingRouteProposalRepository} の MyBatis 実装（出力アダプタ）。 */
@Repository
public class MyBatisBookingRouteProposalRepository implements BookingRouteProposalRepository {

    private final RouteProposalMapper mapper;

    public MyBatisBookingRouteProposalRepository(RouteProposalMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 保存する。
     *
     * <p><strong>提案と候補を 1 つの操作として書く。</strong> 候補だけ消えた提案や、
     * 前回の候補が混ざった提案という中途半端な状態を作らない。
     */
    @Override
    @Transactional
    public void save(BookingRouteProposal proposal) {
        RouteProposalRecord row = toRecord(proposal);
        RouteProposalRecord existing =
                mapper.findByBookingId(proposal.bookingId().value());

        long proposalId;
        if (existing == null) {
            mapper.insert(row);
            proposalId = row.getId();
        } else {
            proposalId = existing.getId();
            // 選択を先に外す。**外部キーが候補を指したままでは消せない**
            mapper.clearSelectedRoute(proposalId);
            if (mapper.update(row) != 1) {
                // **黙って上書きしない。** 別の担当者が先に算出していた場合、
                // 後の保存を通すと前の候補が理由も残さず消える
                throw new ConcurrentModificationException(
                        "別の担当者が先に経路候補を算出しました。最新の内容を確認してください");
            }
            // 再算出は候補を丸ごと入れ替える（ビジネスルール 5）
            mapper.deleteCandidates(proposalId);
        }

        if (!proposal.candidates().isEmpty()) {
            mapper.insertCandidates(toCandidateRecords(proposalId, proposal.candidates()));
        }
        if (proposal.isSelected()) {
            mapper.selectRoute(proposalId, proposal.selectedRoute().voyageNumber().value());
        }
    }

    @Override
    public Optional<BookingRouteProposal> findByBookingId(RoutingBookingId bookingId) {
        RouteProposalRecord row = mapper.findByBookingId(bookingId.value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(row, mapper.findCandidates(row.getId())));
    }

    private static RouteProposalRecord toRecord(BookingRouteProposal proposal) {
        RoutingCriteria criteria = proposal.criteria();
        RouteProposalRecord row = new RouteProposalRecord();
        row.setBookingId(proposal.bookingId().value());
        row.setOriginUnlocode(criteria.origin().unlocode());
        row.setDestinationUnlocode(criteria.destination().unlocode());
        row.setArrivalDeadline(criteria.arrivalDeadline());
        row.setOriginalArrivalDeadline(criteria.originalArrivalDeadline());
        row.setCargoType(criteria.cargoType().name());
        row.setWeight(criteria.weight().kilograms());
        row.setMaxTransitCount(criteria.maxTransitCount());
        row.setCalculationCount(proposal.calculationCount());
        row.setCandidateCount(proposal.candidateCount());

        row.setVersion(proposal.version());
        return row;
    }

    private static List<ProposedRouteRecord> toCandidateRecords(
            long proposalId, List<ProposedRoute> candidates) {
        List<ProposedRouteRecord> rows = new ArrayList<>(candidates.size());
        for (ProposedRoute candidate : candidates) {
            ProposedRouteRecord row = new ProposedRouteRecord();
            row.setProposalId(proposalId);
            row.setVoyageNumber(candidate.voyageNumber().value());
            row.setTransitPorts(encodePorts(candidate.transitPorts()));
            row.setBoardingIndex(candidate.legRange().boardingIndex());
            row.setLandingIndex(candidate.legRange().landingIndex());
            row.setDepartureDate(candidate.departureTime());
            row.setArrivalDate(candidate.arrivalTime());
            row.setTransitDays(candidate.transitDays());
            row.setEstimatedCostValue(candidate.estimatedCost().value());
            row.setEstimatedCostCurrency(candidate.estimatedCost().currency());
            row.setCapacityAvailable(candidate.capacityAvailable());
            row.setHazardousAllowed(candidate.hazardousAllowed());
            row.setRefrigeratedAllowed(candidate.refrigeratedAllowed());
            row.setDeadlineSatisfied(candidate.deadlineSatisfied());
            row.setPriority(candidate.priority());
            rows.add(row);
        }
        return rows;
    }

    private static BookingRouteProposal toDomain(
            RouteProposalRecord row, List<ProposedRouteRecord> candidates) {
        RoutingCriteria criteria = new RoutingCriteria(
                Location.of(row.getOriginUnlocode()),
                Location.of(row.getDestinationUnlocode()),
                row.getArrivalDeadline(),
                row.getOriginalArrivalDeadline(),
                RoutingCargoType.valueOf(row.getCargoType()),
                RoutingWeight.ofKilograms(row.getWeight()),
                row.getMaxTransitCount());

        // **貨物種別は探索条件から渡す。** 候補の側だけを読み戻すと、
        // 「この貨物は何か」が落ちて選択可否が常に真になる
        List<ProposedRoute> routes = candidates.stream()
                .map(c -> toCandidate(c, criteria.cargoType()))
                .toList();

        // 選択済みの候補は、候補の中から同じ航海番号のものを指す。
        // **別の実体を作らない。** 二重に持つと、選択だけが古いままになりうる
        ProposedRoute selected = row.getSelectedVoyageNumber() == null ? null
                : routes.stream()
                        .filter(r -> r.voyageNumber().value()
                                .equals(row.getSelectedVoyageNumber()))
                        .findFirst()
                        .orElse(null);

        return BookingRouteProposal.reconstruct(
                new RoutingBookingId(row.getBookingId()),
                criteria,
                routes,
                row.getCalculationCount(),
                selected,
                row.getVersion());
    }

    private static ProposedRoute toCandidate(
            ProposedRouteRecord row, RoutingCargoType requestedCargoType) {
        return ProposedRoute.reconstruct(
                new VoyageNumber(row.getVoyageNumber()),
                new ProposedRoute.Path(
                        decodePorts(row.getTransitPorts()),
                        new ProposedRoute.LegRange(
                                row.getBoardingIndex(), row.getLandingIndex())),
                new ProposedRoute.Timing(
                        row.getDepartureDate(), row.getArrivalDate(), row.getTransitDays()),
                new Money(row.getEstimatedCostValue(), row.getEstimatedCostCurrency()),
                new ProposedRoute.Handling(
                        requestedCargoType,
                        row.isHazardousAllowed(),
                        row.isRefrigeratedAllowed(),
                        row.isCapacityAvailable()),
                row.isDeadlineSatisfied(),
                row.getPriority());
    }

    /** 経由港はカンマ区切りで保存する。直行は空文字（NULL にしない）。 */
    private static String encodePorts(List<Location> ports) {
        return ports.stream().map(Location::unlocode).collect(Collectors.joining(","));
    }

    private static List<Location> decodePorts(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(Location::of)
                .toList();
    }
}
