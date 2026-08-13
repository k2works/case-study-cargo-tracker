/**
 * 経路コンテキスト。航海スケジュールの管理と経路候補の算出を責務とする。RoutingStatus を所有する（ADR-005）。
 *
 * <p>本パッケージは境界付けられたコンテキストのルートである。トップレベルパッケージと
 * BC は 1 対 1 に対応し、ArchUnit の slices ルールがこの前提を検証する
 * （docs/design/test_strategy.md §3.3 ルール 4・5）。
 *
 * <p>他の BC のクラスを直接参照してはならない。連携は ACL ポートまたは
 * ドメインイベントを経由する（docs/design/domain-model.md「BC 間 ACL ポート一覧」）。
 *
 * <p>内部構成は docs/design/architecture_backend.md「パッケージ構成（全 BC 共通の正典）」に従う。
 */
package com.example.cargotracker.routing;
