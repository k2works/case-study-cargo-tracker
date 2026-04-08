package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.repository.VoyageRepository;
import com.example.cargotracker.shared.domain.model.Location;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class VoyageQueryService {

    private final VoyageRepository voyageRepository;

    public VoyageQueryService(VoyageRepository voyageRepository) {
        this.voyageRepository = voyageRepository;
    }

    /**
     * 全航海を返す。
     */
    public List<Voyage> findAll() {
        return voyageRepository.findAll();
    }

    /**
     * 出発地・目的地・出発期間・到着期限で航海を検索する。
     * 各引数が null の場合はその条件を無視する。
     */
    public List<Voyage> searchVoyages(String originUnlocode, String destinationUnlocode,
                                       LocalDate fromDate, LocalDate arrivalDeadline) {
        Location origin = originUnlocode != null && !originUnlocode.isBlank()
                ? new Location(originUnlocode) : null;
        Location destination = destinationUnlocode != null && !destinationUnlocode.isBlank()
                ? new Location(destinationUnlocode) : null;
        return voyageRepository.findByRoute(origin, destination, fromDate, arrivalDeadline);
    }
}
