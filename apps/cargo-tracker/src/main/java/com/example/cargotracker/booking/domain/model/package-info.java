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
 */
package com.example.cargotracker.booking.domain.model;
