package com.example.cargotracker.simulation.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * シミュレーションの設定（US33 §受入基準 4 / [ADR-0020]）。
 *
 * <p><b>環境変数の名前を推測させない。</b> リラックスバインディングがどの環境変数名に
 * 対応するかは規則を知らないと読めず、外したときは「既定のまま静かに無効」になって
 * 気づけない——`application.yml` で {@code ${ENV:default}} として明示的に受ける
 * （authms の `demo-users` で実測した形）。</p>
 *
 * @param enabled 実行を許可するか。<b>既定は無効</b>——本番では実データに紛れる
 *     貨物を作らない
 * @param gatewayUrl 工程が叩く先。<b>Gateway を通る</b>（[ADR-0020] 決定 2）
 */
@ConfigurationProperties(prefix = "cargo-tracker.simulation")
public record SimulationProperties(boolean enabled, String gatewayUrl) {
}
