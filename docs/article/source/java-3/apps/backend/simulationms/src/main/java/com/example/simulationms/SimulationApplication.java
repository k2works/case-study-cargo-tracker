package com.example.simulationms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// 継続実行の刻みを回す（US37・[ADR-031] 決定 2）。**外部のジョブ基盤を持ち込まない**
@org.springframework.scheduling.annotation.EnableScheduling
public class SimulationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulationApplication.class, args);
    }
}
