/**
 * 全 BC に効く技術基盤の設定。
 *
 * <p>業務の時計（{@code Clock}）はここで組み立てる。<strong>業務日付は
 * 利用者の暦の上の概念</strong>であり、サーバの標準時で判断すると
 * 時差の分だけ「当日」を取りこぼす。
 *
 * <p>共有カーネル（ADR-005 の {@code Location} と {@code ShipperId}）ではない。
 * 共有カーネルの範囲を縛る ArchUnit ルール 6 の対象は {@code shared.domain.model} である。
 */
package com.example.cargotracker.shared.infrastructure.config;
