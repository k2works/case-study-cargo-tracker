package com.example.cargotracker.shipper.infrastructure.repositories;

import com.example.cargotracker.shipper.application.internal.queryservices.ShipperQueryService;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@link ShipperQueryService} の MyBatis 実装（読み取り専用アダプタ）。 */
@Service
public class MyBatisShipperQueryService implements ShipperQueryService {

    private final ShipperQueryMapper mapper;

    public MyBatisShipperQueryService(ShipperQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ShipperView> search(String keyword) {
        return mapper.search(keyword == null ? null : keyword.strip());
    }

    @Override
    public Optional<ShipperView> findById(String shipperId) {
        try {
            return Optional.ofNullable(mapper.findById(UUID.fromString(shipperId)));
        } catch (IllegalArgumentException e) {
            // UUID として解釈できない ID は「見つからない」として扱う。
            // **500 にすると、URL を直接編集しただけで障害に見える。**
            return Optional.empty();
        }
    }
}
