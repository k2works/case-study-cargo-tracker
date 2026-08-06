package com.example.cargotracker.routing.infrastructure.repositories;

import com.example.cargotracker.routing.domain.model.CarrierMovement;
import com.example.cargotracker.routing.domain.model.CarrierName;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.Schedule;
import com.example.cargotracker.routing.domain.model.VesselName;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.domain.model.Location;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** {@link VoyageRepository} の MyBatis 実装（出力アダプタ）。 */
@Repository
public class MyBatisVoyageRepository implements VoyageRepository {

    private final VoyageMapper mapper;

    public MyBatisVoyageRepository(VoyageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Voyage voyage) {
        VoyageRecord row = toRecord(voyage);
        mapper.insert(row);

        List<CarrierMovement> movements = voyage.schedule().carrierMovements();
        List<CarrierMovementRecord> rows = new ArrayList<>(movements.size());
        for (int i = 0; i < movements.size(); i++) {
            rows.add(toMovementRecord(row.getId(), movements.get(i), i));
        }
        mapper.insertMovements(rows);
    }

    @Override
    public Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber) {
        VoyageRecord row = mapper.findByVoyageNumber(voyageNumber.value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(row, mapper.findMovements(row.getId())));
    }

    @Override
    public boolean existsByVoyageNumber(VoyageNumber voyageNumber) {
        return mapper.countByVoyageNumber(voyageNumber.value()) > 0;
    }

    private static VoyageRecord toRecord(Voyage voyage) {
        VoyageRecord row = new VoyageRecord();
        row.setVoyageNumber(voyage.voyageNumber().value());
        row.setVesselName(voyage.vesselName().value());
        row.setCarrierName(voyage.carrierName().value());
        row.setCargoTypes(encodeCargoTypes(voyage.acceptableCargoTypes()));
        row.setVersion(voyage.version());
        return row;
    }

    private static CarrierMovementRecord toMovementRecord(
            Long voyageId, CarrierMovement movement, int seq) {
        CarrierMovementRecord row = new CarrierMovementRecord();
        row.setVoyageId(voyageId);
        row.setDepartureLocationUnlocode(movement.departureLocation().unlocode());
        row.setArrivalLocationUnlocode(movement.arrivalLocation().unlocode());
        row.setDepartureDate(movement.departureTime());
        row.setArrivalDate(movement.arrivalTime());
        row.setSeqNumber(seq);
        return row;
    }

    private static Voyage toDomain(VoyageRecord row, List<CarrierMovementRecord> movements) {
        Schedule schedule = Schedule.of(movements.stream()
                .map(m -> CarrierMovement.of(
                        Location.of(m.getDepartureLocationUnlocode()),
                        Location.of(m.getArrivalLocationUnlocode()),
                        m.getDepartureDate(),
                        m.getArrivalDate()))
                .toList());

        return Voyage.reconstruct(
                new VoyageNumber(row.getVoyageNumber()),
                new VesselName(row.getVesselName()),
                new CarrierName(row.getCarrierName()),
                schedule,
                decodeCargoTypes(row.getCargoTypes()),
                row.getVersion());
    }

    /**
     * 貨物種別をカンマ区切りで保存する。
     *
     * <p><strong>列挙子名を並び順に依存させない。</strong> 名前で書き、名前で読む。
     * 序数（{@code ordinal()}）で保存すると、列挙子を並べ替えただけで
     * 保存済みのデータの意味が変わる。
     */
    static String encodeCargoTypes(Set<RoutingCargoType> types) {
        return types.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    static Set<RoutingCargoType> decodeCargoTypes(String value) {
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(RoutingCargoType::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
