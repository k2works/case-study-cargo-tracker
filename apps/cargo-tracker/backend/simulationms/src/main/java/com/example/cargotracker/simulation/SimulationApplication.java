package com.example.cargotracker.simulation;

import com.example.cargotracker.shared.infrastructure.axon.AxonJdbcConfiguration;
import com.example.cargotracker.shared.infrastructure.axon.AxonServerStartupCheckConfiguration;
import com.example.cargotracker.shared.infrastructure.time.BusinessClockConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 業務シミュレーションの起動クラス（UC23 / [ADR-0020]）。
 *
 * <p><b>業務ではなく、業務が成立していることを確かめる手段である。</b> 各工程は
 * Gateway 経由の HTTP で本番の API を叩く——専用の書き込み経路を作ると、
 * 「シミュレーションは通るのに実際の操作は通らない」状態を検出できなくなる。</p>
 */
// 共有設定は必要なものだけを明示的に取り込む（一括スキャンにしない）。
@SpringBootApplication
@Import({
    AxonJdbcConfiguration.class,
    AxonServerStartupCheckConfiguration.class,
    BusinessClockConfiguration.class,
})
public class SimulationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulationApplication.class, args);
    }
}
