package com.example.cargotracker.simulation.infrastructure.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 継続実行の設定（US36 §受入基準 2・6）。
 *
 * <p><b>既定は無効</b>（§6）。実行そのものが許可された環境でも、継続実行は
 * もう 1 段の許可を要る——流し続ける側は<b>業務を止めうる</b>（この局面に固有の
 * 危険 3）。「実行できる環境＝流し続けてよい環境」にしない。</p>
 *
 * <p><b>環境変数の名前を推測させない。</b> リラックスバインディングがどの環境変数名に
 * 対応するかは規則を知らないと読めず、外したときは「既定のまま静かに無効」になって
 * 気づけない。</p>
 *
 * @param enabled 継続実行を許可するか。<b>既定は無効</b>
 * @param interval 実行を始める間隔。<b>0 にできない</b>——間を空けないと業務が止まる
 * @param maxConcurrent 同時に走らせる本数の上限
 * @param exceptionRatio 例外シナリオを選ぶ割合（0〜1）
 */
@ConfigurationProperties(prefix = "cargo-tracker.simulation.schedule")
public record SimulationScheduleProperties(
        boolean enabled,
        Duration interval,
        int maxConcurrent,
        BigDecimal exceptionRatio) {
}
