package com.example.cargotracker.shipper.infrastructure.acl;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.ShipperExistenceChecker;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.springframework.stereotype.Component;

/**
 * {@link ShipperExistenceChecker} の実装（Shipper 側のアダプタ）。
 *
 * <p><strong>実装を Shipper 側に置くのは、依存の向きを一方向に保つためである。</strong>
 * Booking 側に置くと、Booking のインフラ層が Shipper のリポジトリを知ることになり、
 * ACL を挟んでも Booking → Shipper の実体依存が残る。ポートの定義は利用側（Booking）、
 * 実装は提供側（Shipper）に置くことで、越境は「Booking が定義した契約を Shipper が満たす」
 * という 1 方向だけになる。
 *
 * <p>返すのは存在の有無のみで、荷主のドメインオブジェクトは境界の外に出さない。
 */
@Component
public class ShipperExistenceCheckerAdapter implements ShipperExistenceChecker {

    private final ShipperRepository shipperRepository;

    public ShipperExistenceCheckerAdapter(ShipperRepository shipperRepository) {
        this.shipperRepository = shipperRepository;
    }

    @Override
    public boolean exists(ShipperId shipperId) {
        return shipperId != null && shipperRepository.findById(shipperId).isPresent();
    }
}
