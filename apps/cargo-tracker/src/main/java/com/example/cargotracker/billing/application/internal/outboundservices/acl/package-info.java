/**
 * 請求コンテキストが外へ問い合わせる約束（ACL ポート）。
 *
 * <p><strong>ポートを定義するのは利用する側である</strong>（ADR-005 / ADR-007）。
 * 実装は提供側の BC の {@code infrastructure/acl} に置く。
 *
 * <p><strong>運ぶのは素の値だけである。</strong> 相手の値オブジェクトをそのまま
 * 受け取ると、型を分けた意味が消える。
 *
 * <p>Release 2.0 で最も越境が多かった BC である（請求は予約・荷主・追跡・荷役の
 * すべてを見る必要がある）。<strong>だからこそ入口を 1 つのパッケージに集める。</strong>
 */
package com.example.cargotracker.billing.application.internal.outboundservices.acl;
