package com.example.cargotracker.routing.infrastructure.repositories;

import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageLeg;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link VoyageRepository} の MyBatis ベース実装。
 */
@Repository
public class VoyageRepositoryImpl implements VoyageRepository {

    private final VoyageMapper voyageMapper;

    public VoyageRepositoryImpl(VoyageMapper voyageMapper) {
        this.voyageMapper = voyageMapper;
    }

    @Override
    public List<Voyage> searchVoyages(String originLocode, String destinationLocode) {
        return voyageMapper.searchVoyageNumbers(originLocode, destinationLocode)
            .stream()
            .map(voyageNumber -> {
                VoyageRecord voyageRecord = voyageMapper.findVoyageByNumber(voyageNumber).orElseThrow();
                List<VoyageLegRecord> legs = voyageMapper.findLegsByVoyageNumber(voyageNumber);
                return toVoyage(voyageRecord, legs);
            })
            .toList();
    }

    @Override
    public Optional<Voyage> findByVoyageNumber(String voyageNumber) {
        return voyageMapper.findVoyageByNumber(voyageNumber)
            .map(voyageRecord -> {
                List<VoyageLegRecord> legs = voyageMapper.findLegsByVoyageNumber(voyageNumber);
                return toVoyage(voyageRecord, legs);
            });
    }

    @Override
    public List<Voyage> findAll() {
        return voyageMapper.findAllVoyageNumbers()
            .stream()
            .map(vn -> {
                VoyageRecord vr = voyageMapper.findVoyageByNumber(vn).orElseThrow();
                List<VoyageLegRecord> legs = voyageMapper.findLegsByVoyageNumber(vn);
                return toVoyage(vr, legs);
            })
            .toList();
    }

    private Voyage toVoyage(VoyageRecord record, List<VoyageLegRecord> legRecords) {
        Set<CargoType> cargoTypes = Arrays.stream(record.supportedCargoTypes().split(","))
            .map(String::trim)
            .map(CargoType::valueOf)
            .collect(Collectors.toSet());

        List<VoyageLeg> legs = legRecords.stream()
            .sorted((a, b) -> Integer.compare(a.legOrder(), b.legOrder()))
            .map(l -> new VoyageLeg(
                l.originLocode(),
                l.destinationLocode(),
                l.departureDate(),
                l.arrivalDate()
            ))
            .toList();

        return new Voyage(record.voyageNumber(), record.carrierName(), cargoTypes, legs);
    }
}
