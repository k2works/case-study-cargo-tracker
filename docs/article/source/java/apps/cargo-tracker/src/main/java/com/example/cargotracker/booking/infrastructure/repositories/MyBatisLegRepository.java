package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.domain.model.repository.LegRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.shared.domain.model.Location;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class MyBatisLegRepository implements LegRepository {

    private final LegMapper legMapper;
    private final CargoMapper cargoMapper;

    public MyBatisLegRepository(LegMapper legMapper, CargoMapper cargoMapper) {
        this.legMapper = legMapper;
        this.cargoMapper = cargoMapper;
    }

    @Override
    public void saveAll(BookingId bookingId, List<Leg> legs) {
        Long cargoId = cargoMapper.findIdByBookingId(bookingId.toString());
        List<LegRecord> records = new ArrayList<>();
        for (int i = 0; i < legs.size(); i++) {
            records.add(toRecord(legs.get(i), i + 1));
        }
        legMapper.insertAll(cargoId, records);
    }

    @Override
    public List<Leg> findByBookingId(BookingId bookingId) {
        Long cargoId = cargoMapper.findIdByBookingId(bookingId.toString());
        if (cargoId == null) {
            return List.of();
        }
        return legMapper.findByCargoId(cargoId).stream().map(this::toDomain).toList();
    }

    private LegRecord toRecord(Leg leg, int seqNumber) {
        LegRecord record = new LegRecord();
        record.setVoyageNumber(leg.voyageNumber());
        record.setLoadLocationUnlocode(leg.loadLocation().unlocode());
        record.setUnloadLocationUnlocode(leg.unloadLocation().unlocode());
        record.setLoadTime(leg.loadTime());
        record.setUnloadTime(leg.unloadTime());
        record.setSeqNumber(seqNumber);
        return record;
    }

    private Leg toDomain(LegRecord record) {
        return new Leg(
                record.getVoyageNumber(),
                new Location(record.getLoadLocationUnlocode()),
                new Location(record.getUnloadLocationUnlocode()),
                record.getLoadTime(),
                record.getUnloadTime()
        );
    }
}
