package com.example.cargotracker.booking.infrastructure.query;

import com.example.cargotracker.booking.infrastructure.persistence.ShipperMapper;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.ExistsShipperEmailQuery;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.FindShipperQuery;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.FindShippersQuery;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.ShipperListView;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.ShipperView;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 荷主の問い合わせ。読み取りモデルは投影テーブルだけを見る（他サービスの DB を JOIN しない）。 */
@Component
public class ShipperQueryHandler {

    private final ShipperMapper shippers;

    public ShipperQueryHandler(ShipperMapper shippers) {
        this.shippers = shippers;
    }

    @QueryHandler
    public boolean handle(ExistsShipperEmailQuery query) {
        return shippers.countByEmail(query.email()) > 0;
    }

    @QueryHandler
    public ShipperView handle(FindShipperQuery query) {
        ShipperMapper.ShipperRow row = shippers.findById(query.shipperId());
        return row == null ? null : toView(row);
    }

    @QueryHandler
    public ShipperListView handle(FindShippersQuery query) {
        int size = Math.clamp(query.size(), 1, 200);
        int offset = Math.max(query.page(), 0) * size;
        return new ShipperListView(shippers.findAll(size, offset).stream().map(ShipperQueryHandler::toView).toList());
    }

    private static ShipperView toView(ShipperMapper.ShipperRow row) {
        return new ShipperView(
                row.shipperId(),
                row.shipperCode(),
                row.shipperType(),
                row.name(),
                row.email(),
                row.phone(),
                row.address(),
                row.contractNumber(),
                row.discountRate());
    }
}
