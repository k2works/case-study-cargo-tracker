package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 動作確認用データ（{@code db/demo}）の適用範囲を検証する。
 *
 * <p><strong>動作確認用データが本番のマイグレーションに混ざってはならない。</strong>
 * {@code db/demo} は local / dev プロファイルの {@code spring.flyway.locations} に
 * 明示的に追加した場合にのみ適用される。既定の locations には含めない。
 *
 * <p>test プロファイルは既定の locations を使うため、ここでデータが存在したら
 * それは「本番にも入る経路がある」ことを意味する。
 */
class DemoDataTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private ShipperRepository repository;

    @Test
    void 既定のマイグレーションに動作確認用データは含まれない() {
        assertThat(repository.findByEmail(
                        new com.example.cargotracker.shipper.domain.model.Email("shipper-sample@example.com")))
                .isEmpty();
    }
}
