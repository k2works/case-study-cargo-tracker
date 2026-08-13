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
 * <p><strong>直下にクラスは置かない。</strong>
 */
package com.example.cargotracker.tracking.domain.model;
