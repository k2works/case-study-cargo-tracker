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
}
