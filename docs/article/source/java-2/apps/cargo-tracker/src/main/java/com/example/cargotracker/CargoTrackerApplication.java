package com.example.cargotracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 国際貨物輸送管理システムの起動クラス。
 *
 * <p>本システムはモジュラーモノリスであり、6 つの境界付けられたコンテキスト
 * （booking / shipper / routing / tracking / billing / estimation）と共有カーネルで構成される。
 * 各 BC はトップレベルパッケージに 1 対 1 で対応する。
 *
 * <p>外部システムとの HTTP 連携は行わない（ADR-006）。経路算出・通関・決済・港湾・通知は
 * いずれも内部シミュレーションとして実装する。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CargoTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CargoTrackerApplication.class, args);
    }
}
