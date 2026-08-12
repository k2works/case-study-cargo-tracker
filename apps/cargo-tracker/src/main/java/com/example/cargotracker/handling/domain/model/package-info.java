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
 *
 * <p><strong>構成要素ごとにサブパッケージへ分けている</strong>（ADR-024）。
 *
 * <ul>
 *   <li>{@code aggregates} —— 集約ルートとその識別子</li>
 *   <li>{@code entities} —— 集約の内側で同一性を持つもの</li>
 *   <li>{@code valueobjects} —— 値オブジェクトと列挙</li>
 *   <li>{@code commands} —— 業務の要求をまとめた型</li>
 * </ul>
 *
 * <p><strong>ここ（直下）に残すのはドメインサービスと例外である。</strong>
 * どれにも属さないためであり、参照実装（practical-ddd-in-enterprise-java）も
 * サービスの置き場を持たない。
 */
package com.example.cargotracker.handling.domain.model;
