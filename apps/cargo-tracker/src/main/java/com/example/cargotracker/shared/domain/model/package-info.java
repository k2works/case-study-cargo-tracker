/**
 * 共有カーネル（Shared Kernel）。
 *
 * <p><strong>ここに置いてよいのは {@code Location} と {@code ShipperId} の 2 つのみである</strong>（ADR-005）。
 * ArchUnit のルール 6 がこれを検証する（test_strategy.md §3.3）。
 *
 * <p>共有カーネルは最も変更コストが高い。1 クラス増えるたびに全 BC の再ビルドとレビューを
 * 強制するため、「全 BC から使うから」は追加の理由にならない。認証の {@code UserAccount} は
 * この規律に従い {@code security} サブドメインへ分離した。
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
package com.example.cargotracker.shared.domain.model;
