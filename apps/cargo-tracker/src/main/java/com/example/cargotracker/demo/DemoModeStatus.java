package com.example.cargotracker.demo;

import java.util.List;

/**
 * デモモードの状況。画面の帯と、それを更新する問い合わせが読む。
 *
 * @param running           動いているか
 * @param activeCargo       いま進めている貨物の数
 * @param completedCargo    請求まで通した貨物の数
 * @param failedCargo       途中で止まった貨物の数
 * @param refreshIntervalMs 業務画面を再読み込みする間隔（ミリ秒）
 * @param recentEvents      直近の出来事（新しい順）
 */
record DemoModeStatus(
        boolean running,
        int activeCargo,
        int completedCargo,
        int failedCargo,
        long refreshIntervalMs,
        List<DemoModeEvent> recentEvents) {
}
