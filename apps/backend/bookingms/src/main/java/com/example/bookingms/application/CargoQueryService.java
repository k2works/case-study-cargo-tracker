package com.example.bookingms.application;

import com.example.bookingms.domain.projections.CargoSummary;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoSummaryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 貨物予約の Read Model 参照サービス（US04）。
 */
@Service
public class CargoQueryService {

    private final CargoSummaryMapper cargoSummaryMapper;

    public CargoQueryService(CargoSummaryMapper cargoSummaryMapper) {
        this.cargoSummaryMapper = cargoSummaryMapper;
    }

    public CargoSummary findByBookingId(String bookingId) {
        return cargoSummaryMapper.findByBookingId(bookingId);
    }

    public List<CargoSummary> findAll() {
        return cargoSummaryMapper.findAll();
    }

    public List<CargoSummary> findAll(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        return cargoSummaryMapper.findAllPaged(safePage * safeSize, safeSize);
    }

    public long count() {
        return cargoSummaryMapper.countAll();
    }
}
