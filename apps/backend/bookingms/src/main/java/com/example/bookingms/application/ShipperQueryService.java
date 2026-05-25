package com.example.bookingms.application;

import com.example.bookingms.domain.projections.ShipperProjection;
import com.example.bookingms.infrastructure.repositories.mybatis.ShipperMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 荷主の Read Model 参照サービス（US02）。
 *
 * <p>MyBatis Mapper を呼び出して {@link ShipperProjection} を返却する。</p>
 */
@Service
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

    public List<ShipperProjection> findAll() {
        return shipperMapper.findAll();
    }
}
