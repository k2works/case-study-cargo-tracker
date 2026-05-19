// Shared Kernel モジュール ビルドスクリプト
//
// 役割: 複数マイクロサービスが共有する Event クラスを提供する（ADR-0014）。
// 依存: Axon の @EventTag アノテーションのみ（Spring Boot 非依存の軽量モジュール）。

plugins {
    java
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // @EventTag アノテーションのために axon-messaging を参照する
    implementation(libs.axon.spring.boot.starter)
}
