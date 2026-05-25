package com.example.bookingms.application;

import com.example.bookingms.domain.projections.ShipperProjection;
import com.example.bookingms.infrastructure.repositories.mybatis.ShipperMapper;
import com.example.bookingms.interfaces.rest.dto.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 荷主の Read Model 参照サービス（US02 / ADR-0008）。
 *
 * <p>サニタイズは {@link PageRequest} に集約されているため、本サービスは
 * 受け取った {@link PageRequest} の値をそのまま Mapper に委譲する。</p>
 */
@Service
@Transactional(readOnly = true)
public class ShipperQueryService {

    private final ShipperMapper shipperMapper;

    public ShipperQueryService(ShipperMapper shipperMapper) {
        this.shipperMapper = shipperMapper;
    }

    public ShipperProjection findByShipperId(String shipperId) {
        return shipperMapper.findByShipperId(shipperId);
    }

    public List<ShipperProjection> findByEmail(String email) {
        return shipperMapper.findByEmail(email);
    }

    public List<ShipperProjection> findAll(PageRequest pageRequest) {
        return shipperMapper.findAllPaged(pageRequest.offset(), pageRequest.size());
    }

    public long count() {
        return shipperMapper.countAll();
    }
}
