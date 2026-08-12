package com.example.cargotracker.shipper.infrastructure.acl;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .ShipperDiscountPort;
import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link ShipperDiscountPort} の実装（ACL のアダプタ。US22）。
 *
 * <p><strong>返すのは素の値だけである</strong>（ADR-005）。Shipper の
 * {@code DiscountRate} を返すと、Billing が Shipper のドメインを参照することになる
 * （ArchUnit ルール 4）。
 *
 * <p><strong>見つからない荷主を例外にしない。</strong> 空を返す＝割引なしである。
 * ここで止めると<strong>請求そのものが止まる</strong>。
 */
@Component
public class ShipperDiscountAdapter implements ShipperDiscountPort {

    private final ShipperRepository repository;

    public ShipperDiscountAdapter(ShipperRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BigDecimal> findContractDiscountRate(String shipperId) {
        if (shipperId == null || shipperId.isBlank()) {
            return Optional.empty();
        }
        ShipperId id;
        try {
            id = new ShipperId(UUID.fromString(shipperId.strip()));
        } catch (IllegalArgumentException e) {
            // **形式の違う ID を例外にしない。** 請求の画面が 500 になる
            return Optional.empty();
        }
        return repository.findById(id)
                .map(Shipper::contract)
                // **個人荷主は契約を持たない。** null は「割引なし」である
                .map(contract -> contract.discountRate().value());
    }
}
