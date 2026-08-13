package com.example.cargotracker.shipper.infrastructure.repositories;

import com.example.cargotracker.shipper.application.internal.queryservices.ShipperQueryService;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperView;
import com.example.cargotracker.shared.application.paging.Page;
import com.example.cargotracker.shared.application.paging.PageRequest;
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
    public Page<ShipperView> search(String keyword, PageRequest page) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.strip();
        // **総件数は SQL で数える。** 全件を読んでから size() を取ると、
        // ページ送りを入れた意味が無くなる
        long total = mapper.count(normalized);
        return Page.of(
                mapper.search(normalized, page.offset(), page.limit()).stream()
                        .map(MyBatisShipperQueryService::toView)
                        .toList(),
                page, total);
    }

    @Override
    public Optional<ShipperView> findById(String shipperId) {
        UUID id;
        try {
            id = UUID.fromString(shipperId);
        } catch (IllegalArgumentException e) {
            // UUID として解釈できない ID は「見つからない」として扱う。
            // **500 にすると、URL を直接編集しただけで障害に見える。**
            //
            // **catch は解析だけを囲む。** 読み出しまで囲むと、読み出し側が投げた
            // 例外が「見つかりません」に化けて原因が残らない（IT15 の P2）
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findById(id))
                .map(MyBatisShipperQueryService::toView);
    }

    /** 生の行を表示用へ組み立てる（入れ子は SQL では作れない。IT17 の R6）。 */
    private static ShipperView toView(ShipperQueryRow row) {
        return new ShipperView(
                row.getId(),
                row.getShipperCode(),
                new ShipperView.Type(row.getShipperType(), row.getTypeLabel()),
                new ShipperView.Contact(row.getName(), row.getEmail(), row.getPhone()),
                new ShipperView.Address(
                        row.getAddress(),
                        row.getAddressCountry(),
                        row.getAddressPostalCode(),
                        row.getAddressRegion(),
                        row.getAddressCity(),
                        row.getAddressStreet()),
                new ShipperView.Contract(
                        row.getContractNumber(), row.getDiscountRatePercentage()),
                row.getVersion());
    }
}
