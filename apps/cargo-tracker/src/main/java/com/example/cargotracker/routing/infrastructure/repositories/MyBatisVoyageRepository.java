package com.example.cargotracker.routing.infrastructure.repositories;

import com.example.cargotracker.routing.domain.model.CarrierMovement;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.repository.VoyageRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.shared.domain.model.Location;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisVoyageRepository implements VoyageRepository {

    private final VoyageMapper voyageMapper;

    public MyBatisVoyageRepository(VoyageMapper voyageMapper) {
        this.voyageMapper = voyageMapper;
    }

    @Override
    public void save(Voyage voyage) {
        VoyageRecord record = toRecord(voyage);
        voyageMapper.insertVoyage(record);
        List<CarrierMovementRecord> movementRecords = voyage.getSchedule().carrierMovements().stream()
                .map(m -> toMovementRecord(record.getId(), m))
                .toList();
        if (!movementRecords.isEmpty()) {
            voyageMapper.insertCarrierMovements(record.getId(), movementRecords);
        }
    }

    @Override
    public Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber) {
        VoyageRecord record = voyageMapper.findVoyageByVoyageNumber(voyageNumber.number());
        if (record == null) return Optional.empty();
        return Optional.of(toDomain(record));
    }

    @Override
    public List<Voyage> findByRoute(Location origin, Location destination,
                                     LocalDate fromDate, LocalDate arrivalDeadline) {
        String originUnlocode = origin != null ? origin.unlocode() : null;
        String destinationUnlocode = destination != null ? destination.unlocode() : null;
        return voyageMapper.findVoyagesByRoute(originUnlocode, destinationUnlocode, fromDate, arrivalDeadline)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Voyage> findAll() {
        return voyageMapper.findAllVoyages().stream().map(this::toDomain).toList();
    }

    private VoyageRecord toRecord(Voyage voyage) {
        VoyageRecord record = new VoyageRecord();
        record.setVoyageNumber(voyage.getVoyageNumber().number());
        return record;
    }

    private CarrierMovementRecord toMovementRecord(Long voyageId, CarrierMovement movement) {
        CarrierMovementRecord record = new CarrierMovementRecord();
        record.setVoyageId(voyageId);
        record.setDepartureLocationUnlocode(movement.getDepartureLocation().unlocode());
        record.setArrivalLocationUnlocode(movement.getArrivalLocation().unlocode());
        record.setDepartureDate(movement.getDepartureTime());
        record.setArrivalDate(movement.getArrivalTime());
        return record;
    }

    private Voyage toDomain(VoyageRecord record) {
        List<CarrierMovementRecord> movementRecords = voyageMapper.findMovementsByVoyageId(record.getId());
        List<CarrierMovement> movements = movementRecords.stream()
                .map(m -> new CarrierMovement(
                        new Location(m.getDepartureLocationUnlocode()),
                        new Location(m.getArrivalLocationUnlocode()),
                        m.getDepartureDate(),
                        m.getArrivalDate()
                ))
                .toList();
        return new Voyage(record.getId(), new VoyageNumber(record.getVoyageNumber()), Schedule.of(movements));
    }
}
