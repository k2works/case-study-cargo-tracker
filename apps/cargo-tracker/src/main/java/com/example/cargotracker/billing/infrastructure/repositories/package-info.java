/**
 * 請求コンテキストの永続化の実装（MyBatis）。
 *
 * <p>マッパー・レコード（DB の行の形）・リポジトリ実装・クエリサービス実装を置く。
 *
 * <p><strong>自分の BC のテーブルだけを触る</strong>（ADR-015。検査で固定している）。
 * 他の BC のテーブルが要るなら ACL ポートを通す。
 */
package com.example.cargotracker.billing.infrastructure.repositories;
