package com.example.cargotracker.billing.infrastructure.repositories;

import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import com.example.cargotracker.billing.domain.model.valueobjects.ChargeStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FreightChargeRepositoryImpl implements FreightChargeRepository {

    private final FreightChargeMapper freightChargeMapper;

    public FreightChargeRepositoryImpl(FreightChargeMapper freightChargeMapper) {
        this.freightChargeMapper = freightChargeMapper;
    }

    @Override
    public void save(FreightCharge charge) {
        LocalDateTime now = LocalDateTime.now();
        String id = charge.getId().value().toString();

        FreightChargeRecord existing = freightChargeMapper.findById(id);
        if (existing == null) {
            FreightChargeRecord row = new FreightChargeRecord(
                    id,
                    charge.getBookingId(),
                    charge.getStatus().name(),
                    charge.getBaseAmount(),
                    charge.getAdjustmentAmount(),
                    charge.getTotalAmount(),
                    now,
                    now
            );
            freightChargeMapper.insert(row);
        } else {
            FreightChargeRecord row = new FreightChargeRecord(
                    id,
                    charge.getBookingId(),
                    charge.getStatus().name(),
                    charge.getBaseAmount(),
                    charge.getAdjustmentAmount(),
                    charge.getTotalAmount(),
                    existing.createdAt(),
                    now
            );
            freightChargeMapper.update(row);
        }
    }

    @Override
    public Optional<FreightCharge> findById(FreightId id) {
        FreightChargeRecord row = freightChargeMapper.findById(id.value().toString());
        return Optional.ofNullable(row).map(this::toFreightCharge);
    }

    @Override
    public List<FreightCharge> findByBookingId(String bookingId) {
        return freightChargeMapper.findByBookingId(bookingId).stream()
                .map(this::toFreightCharge)
                .toList();
    }

    @Override
    public List<FreightCharge> findAll() {
        return freightChargeMapper.findAll().stream()
                .map(this::toFreightCharge)
                .toList();
    }

    private FreightCharge toFreightCharge(FreightChargeRecord row) {
        return FreightCharge.reconstitute(
                new FreightId(UUID.fromString(row.id())),
                row.bookingId(),
                ChargeStatus.valueOf(row.status()),
                row.baseAmount(),
                row.adjustmentAmount(),
                row.totalAmount()
        );
    }
}
