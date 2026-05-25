package com.example.bookingms.application;

import com.example.bookingms.domain.projections.CargoSummary;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoSummaryMapper;
import com.example.bookingms.interfaces.rest.dto.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 貨物予約の Read Model 参照サービス（US04 / ADR-0008）。
 *
 * <p>サニタイズは {@link PageRequest} に集約されているため、本サービスは
 * 受け取った {@link PageRequest} の値をそのまま Mapper に委譲する。</p>
 */
@Service
@Transactional(readOnly = true)
public class CargoQueryService {

    private final CargoSummaryMapper cargoSummaryMapper;

    public CargoQueryService(CargoSummaryMapper cargoSummaryMapper) {
        this.cargoSummaryMapper = cargoSummaryMapper;
    }

    public CargoSummary findByBookingId(String bookingId) {
        return cargoSummaryMapper.findByBookingId(bookingId);
    }

    public List<CargoSummary> findAll(PageRequest pageRequest) {
        return cargoSummaryMapper.findAllPaged(pageRequest.offset(), pageRequest.size());
    }

    public long count() {
        return cargoSummaryMapper.countAll();
    }
}
