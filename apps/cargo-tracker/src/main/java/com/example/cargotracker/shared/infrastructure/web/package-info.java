/**
 * BC に属さない横断的な画面（ダッシュボード・ログイン画面など）。
 *
 * <p><strong>共有カーネルではない。</strong> 共有カーネルは {@code shared.domain.model} に限られる（ADR-005）。
 * 本パッケージは特定の BC に属さない技術的な入口であり、業務のモデルを持たない。
 *
 * <p>ダッシュボードはロール別の作業入口を示す。<strong>ロールに該当しない項目はサーバ側で出さない。</strong>
 * 出したうえで 403 にすると、クリックして突き当たるという体験になる。
 */
package com.example.cargotracker.shared.infrastructure.web;
