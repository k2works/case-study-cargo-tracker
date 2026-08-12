/**
 * 予約コンテキストの集約・エンティティ・値オブジェクト・コマンド。
 *
 * <p>集約ルートは {@code Cargo} である。予約状態（{@code BookingStatus}）と
 * 経路状態（{@code CargoRoutingStatus}）は<strong>別々に動く</strong>。
 * 経路を確定しても予約状態は変わらない（遷移表 3）。
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 * 経路状態は Routing の {@code RoutingStatus} ではなく
 * {@code CargoRoutingStatus} を、区間の航海番号は {@code VoyageNumber} ではなく
 * 文字列を使う。
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
package com.example.cargotracker.booking.domain.model;
