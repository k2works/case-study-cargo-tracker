package com.example.routingms.application;

import com.example.routingms.domain.projections.RouteDesignRequestProjection;
import com.example.routingms.infrastructure.repositories.mybatis.RouteDesignRequestMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 経路設計依頼の参照サービス（US06 / cross-service、ADR-0009）。
 *
 * <p>bookingms から Kafka 経由で受信し routingms に記録した経路設計依頼
 * （route_design_request read model）を参照する。経路設計者の経路設計待ちリスト
 * （IT4 / US08）の入力であり、cross-service 伝搬の確認にも用いる。</p>
 */
@Service
public class RouteDesignRequestQueryService {

    private final RouteDesignRequestMapper mapper;

    public RouteDesignRequestQueryService(RouteDesignRequestMapper mapper) {
        this.mapper = mapper;
    }

    public RouteDesignRequestProjection findByBookingId(String bookingId) {
        return mapper.findByBookingId(bookingId);
    }

    public List<RouteDesignRequestProjection> findAll() {
        return mapper.findAll();
    }
}
