package com.example.cargotracker.shipper.infrastructure.acl;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .ShipperContactPort;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link ShipperContactPort} の実装（ACL のアダプタ。IT14 レビュー C3）。
 *
 * <p><strong>返すのは素の値だけである</strong>（ADR-005）。Shipper の
 * {@code Email} / {@code Phone} を返すと、Billing が Shipper のドメインを参照する。
 */
@Component
public class ShipperContactAdapter implements ShipperContactPort {

    private final ShipperRepository repository;

    public ShipperContactAdapter(ShipperRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Contact> findContact(String shipperId) {
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
                .map(shipper -> new Contact(
                        shipper.name().value(),
                        shipper.contact().email().value(),
                        shipper.contact().phone().value()));
    }
}
