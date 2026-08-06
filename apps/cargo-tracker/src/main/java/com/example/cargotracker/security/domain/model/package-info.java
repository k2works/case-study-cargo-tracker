/**
 * 認証・認可のドメインモデル。
 *
 * <p>{@code UserAccount} 集約がログイン可否の判断を持つ。<strong>ロック状態は集約が保持し
 * 永続化する。</strong> ログイン履歴から都度導出すると、リクエストをまたいだ時点で
 * 誤って解除される（docs/design/domain-model.md「9. Security サブドメイン」）。
 *
 * <p>ロールの値の正典は docs/design/non_functional.md §4.1 である。
 */
package com.example.cargotracker.security.domain.model;
