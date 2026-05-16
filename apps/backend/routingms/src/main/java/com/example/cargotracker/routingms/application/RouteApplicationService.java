package com.example.cargotracker.routingms.application;

import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.RouteSearchSpecification;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitPath;
import com.example.cargotracker.routingms.domain.ports.EdgeRepository;
import com.example.cargotracker.routingms.domain.services.RouteCandidateFinder;
import com.example.cargotracker.routingms.interfaces.rest.dto.RouteCandidatesResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 経路候補算出アプリケーションサービス（US08）。
 */
@Service
public class RouteApplicationService {

    private final EdgeRepository edgeRepository;

    public RouteApplicationService(EdgeRepository edgeRepository) {
        this.edgeRepository = edgeRepository;
    }

    public RouteCandidatesResponse findCandidates(
            String origin, String destination, LocalDate arrivalDeadline, CargoType cargoType) {
        List<TransitEdge> edges = edgeRepository.findAll();
        RouteCandidateFinder finder = new RouteCandidateFinder(edges);
        RouteSearchSpecification spec = RouteSearchSpecification.of(
                origin, destination, arrivalDeadline, cargoType);
        List<TransitPath> paths = finder.findCandidates(spec);
        List<RouteCandidatesResponse.CandidateItem> items = paths.stream()
                .map(p -> toItem(p, spec))
                .toList();
        return new RouteCandidatesResponse(items);
    }

    private RouteCandidatesResponse.CandidateItem toItem(TransitPath path, RouteSearchSpecification spec) {
        List<String> voyageNumbers = path.edges().stream()
                .map(TransitEdge::voyageNumber).toList();
        List<String> via = path.edges().stream()
                .skip(1)
                .map(e -> e.fromUnLocode().value())
                .toList();
        long transitDays = ChronoUnit.DAYS.between(
                path.edges().get(0).departureTime().toLocalDate(),
                path.edges().get(path.edges().size() - 1).arrivalTime().toLocalDate());
        return new RouteCandidatesResponse.CandidateItem(voyageNumbers, via, transitDays);
    }
}
