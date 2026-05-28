package com.example.trackingms.domain.services;

import com.example.trackingms.domain.model.TrackingNumber;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TrackingNumberGenerator} のテスト。
 *
 * <p>採番は推測困難で一意である必要がある。書式は {@link TrackingNumber} の不変条件で
 * 保証されるが、ジェネレータの実装が衝突を起こさないこと（少なくとも 1000 回ループで
 * 重複なし）も担保する。</p>
 */
class TrackingNumberGeneratorTest {

    @Test
    void 採番した追跡番号はTrackingNumberの書式に従う() {
        TrackingNumberGenerator generator = new TrackingNumberGenerator();

        TrackingNumber tn = generator.generate();

        // TrackingNumber コンストラクタが書式を保証するため、ここでは生成自体が
        // 例外を起こさないこと + value が TRK- で始まることを担保
        assertThat(tn.value()).startsWith("TRK-");
        assertThat(tn.value()).hasSize(14);  // TRK- + 10 桁
    }

    @Test
    void 連続採番で衝突が起きない_1000件_実用十分性() {
        // 推測困難性は書式（10 桁・36 文字種）で約 3.6 兆通り。1000 件程度で衝突しない
        // SecureRandom ベース実装の最低限の品質を担保する。
        TrackingNumberGenerator generator = new TrackingNumberGenerator();
        Set<String> seen = new HashSet<>();

        IntStream.range(0, 1000).forEach(i -> seen.add(generator.generate().value()));

        assertThat(seen).hasSize(1000);
    }
}
