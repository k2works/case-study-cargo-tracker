/**
 * 認証・認可のドメインモデル。
 *
 * <p>{@code UserAccount} 集約がログイン可否の判断を持つ。<strong>ロック状態は集約が保持し
 * 永続化する。</strong> ログイン履歴から都度導出すると、リクエストをまたいだ時点で
 * 誤って解除される（docs/design/domain-model.md「9. Security サブドメイン」）。
 *
 * <p>ロールの値の正典は docs/design/non_functional.md §4.1 である。
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
package com.example.cargotracker.security.domain.model;
