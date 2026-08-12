/**
 * 経路コンテキストの集約・エンティティ・値オブジェクト・コマンド。
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 * 貨物種別は Booking の {@code CargoType} ではなく {@code RoutingCargoType} を使う。
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
package com.example.cargotracker.routing.domain.model;
