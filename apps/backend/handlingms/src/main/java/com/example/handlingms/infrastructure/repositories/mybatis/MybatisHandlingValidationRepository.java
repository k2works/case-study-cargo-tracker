package com.example.handlingms.infrastructure.repositories.mybatis;

import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.projections.CargoSnapshot;
import com.example.handlingms.domain.services.HandlingValidationRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * {@link HandlingValidationRepository} の MyBatis 実装
 * （IT8 H2 持ち越し T1.11、DIP 回復のため domain → 実装の依存方向を逆転）。
 *
 * <p>従来 {@code HandlingValidationService} が直接呼んでいた Mapper 2 つをこのクラスで包み、
 * domain 層は {@code HandlingValidationRepository} interface のみに依存させる。</p>
 */
@Component
public class MybatisHandlingValidationRepository implements HandlingValidationRepository {

    private final HandlingActivityMapper handlingActivityMapper;
    private final CargoSnapshotMapper cargoSnapshotMapper;

    public MybatisHandlingValidationRepository(HandlingActivityMapper handlingActivityMapper,
                                               CargoSnapshotMapper cargoSnapshotMapper) {
        this.handlingActivityMapper = handlingActivityMapper;
        this.cargoSnapshotMapper = cargoSnapshotMapper;
    }

    @Override
    public long countDuplicates(String trackingNumber, HandlingType handlingType,
                                String unlocode,
                                LocalDateTime windowStart, LocalDateTime windowEnd) {
        return handlingActivityMapper.countDuplicates(
                trackingNumber, handlingType.name(), unlocode, windowStart, windowEnd);
    }

    @Override
    public CargoSnapshot findCargoSnapshotByTrackingNumber(String trackingNumber) {
        return cargoSnapshotMapper.findByTrackingNumber(trackingNumber);
    }
}
