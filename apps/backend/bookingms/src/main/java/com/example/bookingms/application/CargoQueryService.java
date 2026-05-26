package com.example.bookingms.application;

import com.example.bookingms.domain.projections.CargoLeg;
import com.example.bookingms.domain.projections.CargoSummary;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoLegMapper;
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
    private final CargoLegMapper cargoLegMapper;

    public CargoQueryService(CargoSummaryMapper cargoSummaryMapper, CargoLegMapper cargoLegMapper) {
        this.cargoSummaryMapper = cargoSummaryMapper;
        this.cargoLegMapper = cargoLegMapper;
    }

    public CargoSummary findByBookingId(String bookingId) {
        return cargoSummaryMapper.findByBookingId(bookingId);
    }

    /** 確定旅程（cargo_leg）を leg_seq 順に取得する（US11 / US12 経路表示）。 */
    public List<CargoLeg> findLegs(String bookingId) {
        return cargoLegMapper.findByBookingId(bookingId);
    }

    public List<CargoSummary> findAll(PageRequest pageRequest) {
        return cargoSummaryMapper.findAllPaged(pageRequest.offset(), pageRequest.size());
    }

    public long count() {
        return cargoSummaryMapper.countAll();
    }
}
