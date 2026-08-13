/**
 * 予約コンテキストが他の BC・マスタへ問い合わせる出力ポート（ACL）。
 *
 * <p><strong>ポートを定義するのは利用する側である。</strong> 実装は提供側の BC に置く。
 * ここは BC 間の<strong>唯一の許可された越境点</strong>であり、
 * ArchUnit ルール 4 はこのパッケージだけを除外する。
 */
package com.example.cargotracker.booking.application.internal.outboundservices.acl;
