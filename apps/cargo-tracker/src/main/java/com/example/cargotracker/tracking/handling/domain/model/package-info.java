/**
 * 荷役モジュールの集約・値オブジェクト。
 *
 * <p>Booking Context のクラスを直接参照してはならない。予約の予定ルートは
 * ACL ポート（{@code CargoSnapshots}）を通じて素の値で受け取る。
 *
 * <p>航海番号は Routing の {@code VoyageNumber} ではなく
 * {@code HandlingVoyageNumber} を使う（{@code domain-model.md}
 * 「VoyageNumber のコンテキスト分離設計」）。作業場所は共有カーネルの
 * {@code Location} をそのまま使う（ADR-005）。
 */
package com.example.cargotracker.tracking.handling.domain.model;
