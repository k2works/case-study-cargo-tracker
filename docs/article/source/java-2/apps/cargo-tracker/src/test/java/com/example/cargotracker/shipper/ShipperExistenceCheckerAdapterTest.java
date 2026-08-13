package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import com.example.cargotracker.shipper.infrastructure.acl.ShipperExistenceCheckerAdapter;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Booking → Shipper の ACL アダプタを検証する。
 *
 * <p>アダプタは委譲のみのため、リポジトリをモックしたユニットテストで足りる
 * （{@code test_strategy.md}「ポート実装（Adapter）のテスト」）。
 */
class ShipperExistenceCheckerAdapterTest {

    private final ShipperRepository repository = mock(ShipperRepository.class);
    private final ShipperExistenceCheckerAdapter adapter =
            new ShipperExistenceCheckerAdapter(repository);

    @Test
    void 荷主が存在すればtrueを返す() {
        ShipperId id = ShipperId.generate();
        when(repository.findById(id)).thenReturn(Optional.of(mock(Shipper.class)));

        assertThat(adapter.exists(id)).isTrue();
    }

    @Test
    void 荷主が存在しなければfalseを返す() {
        ShipperId id = ShipperId.generate();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.exists(id)).isFalse();
    }

    /**
     * 荷主 ID の未指定は「存在しない」として扱う。
     *
     * <p>ここで {@code NullPointerException} を投げると、入力の取りこぼしが
     * 業務のエラーではなくシステム障害として現れる。
     */
    @Test
    void 荷主IDが未指定ならfalseを返す() {
        assertThat(adapter.exists(null)).isFalse();
    }
}
