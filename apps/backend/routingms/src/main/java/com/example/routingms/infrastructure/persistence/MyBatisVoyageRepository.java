package com.example.routingms.infrastructure.persistence;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.entities.CarrierMovement;
import com.example.routingms.domain.model.valueobjects.Schedule;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.routingms.domain.ports.VoyageRepository;
import com.example.routingms.infrastructure.persistence.record.CarrierMovementRecord;
import com.example.routingms.infrastructure.persistence.record.VoyageRecord;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis を使った VoyageRepository の実装クラス
 */
@Repository
public class MyBatisVoyageRepository implements VoyageRepository {

    private final VoyageMapper voyageMapper;

    public MyBatisVoyageRepository(VoyageMapper voyageMapper) {
        this.voyageMapper = voyageMapper;
    }

    @Override
    public Voyage save(Voyage voyage) {
        VoyageRecord voyageRec = new VoyageRecord();
        voyageRec.setVoyageNumber(voyage.getVoyageNumber().getNumber());
        voyageMapper.insertVoyage(voyageRec); // useGeneratedKeys=true → id がセットされる

        Long voyageId = voyageRec.getId();
        List<CarrierMovement> movements = voyage.getSchedule().getCarrierMovements();
        for (CarrierMovement movement : movements) {
            CarrierMovementRecord cmRecord = toCarrierMovementRecord(voyageId, movement);
            voyageMapper.insertCarrierMovement(cmRecord);
        }

        return new Voyage(voyageId, voyage.getVoyageNumber(), voyage.getSchedule());
    }

    @Override
    public void update(Voyage voyage) {
        // voyage テーブルの updated_at を更新
        VoyageRecord voyageRec = new VoyageRecord();
        voyageRec.setId(voyage.getId());
        voyageMapper.updateVoyage(voyageRec);

        // carrier_movement を洗い替え
        voyageMapper.deleteCarrierMovementsByVoyageId(voyage.getId());
        List<CarrierMovement> movements = voyage.getSchedule().getCarrierMovements();
        for (CarrierMovement movement : movements) {
            CarrierMovementRecord cmRecord = toCarrierMovementRecord(voyage.getId(), movement);
            voyageMapper.insertCarrierMovement(cmRecord);
        }
    }

    @Override
    public Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber) {
        return voyageMapper.findVoyageByNumber(voyageNumber.getNumber())
                .map(vr -> {
                    List<CarrierMovementRecord> cmRecords =
                            voyageMapper.findCarrierMovementsByVoyageId(vr.getId());
                    return toVoyage(vr, cmRecords);
                });
    }

    @Override
    public List<Voyage> findAll() {
        return voyageMapper.findAllVoyages().stream()
                .map(vr -> {
                    List<CarrierMovementRecord> cmRecords =
                            voyageMapper.findCarrierMovementsByVoyageId(vr.getId());
                    return toVoyage(vr, cmRecords);
                })
                .toList();
    }

    @Override
    public void deleteByVoyageNumber(VoyageNumber voyageNumber) {
        Optional<VoyageRecord> vr = voyageMapper.findVoyageByNumber(voyageNumber.getNumber());
        vr.ifPresent(voyageRec -> {
            voyageMapper.deleteCarrierMovementsByVoyageId(voyageRec.getId());
            voyageMapper.deleteVoyageByNumber(voyageNumber.getNumber());
        });
    }

    // --- private helpers ---

    private CarrierMovementRecord toCarrierMovementRecord(Long voyageId, CarrierMovement movement) {
        CarrierMovementRecord r = new CarrierMovementRecord();
        r.setVoyageId(voyageId);
        r.setDepartureLocationUnlocode(movement.getDepartureLocationUnlocode());
        r.setArrivalLocationUnlocode(movement.getArrivalLocationUnlocode());
        r.setDepartureDate(movement.getDepartureDate());
        r.setArrivalDate(movement.getArrivalDate());
        r.setSeqNumber(movement.getSeqNumber());
        return r;
    }

    private Voyage toVoyage(VoyageRecord vr, List<CarrierMovementRecord> cmRecords) {
        List<CarrierMovement> movements = cmRecords.stream()
                .map(cm -> new CarrierMovement(
                        cm.getDepartureLocationUnlocode(),
                        cm.getArrivalLocationUnlocode(),
                        cm.getDepartureDate(),
                        cm.getArrivalDate(),
                        cm.getSeqNumber()))
                .toList();
        Schedule schedule = new Schedule(movements);
        VoyageNumber voyageNumber = new VoyageNumber(vr.getVoyageNumber());
        return new Voyage(vr.getId(), voyageNumber, schedule);
    }
}
