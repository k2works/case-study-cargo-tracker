/**
 * 追跡コンテキストの集約・エンティティ・値オブジェクト。
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 * 航海番号は Routing の {@code VoyageNumber} ではなく
 * {@code TrackingVoyageNumber} を使う（{@code domain-model.md}
 * 「VoyageNumber のコンテキスト分離設計」）。
 *
 * <p><strong>{@code TransportStatus} を所有するのは本パッケージである</strong>（ADR-005）。
 * 他の BC は ACL ポート経由で、必要な粒度の自前型に変換して参照する。
 */
package com.example.cargotracker.tracking.domain.model;
