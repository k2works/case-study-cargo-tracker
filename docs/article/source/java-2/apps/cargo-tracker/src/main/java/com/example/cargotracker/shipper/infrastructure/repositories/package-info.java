/**
 * 荷主コンテキストの永続化アダプタ（MyBatis）。
 *
 * <p>ドメイン層で定義した出力ポートの実装である。集約と DB のレコードの変換はここに閉じ込める。
 *
 * <p><strong>本パッケージのテストは Testcontainers の実 PostgreSQL で書く</strong>（ADR-003）。
 * H2 で書くと方言差が本番障害として現れる。
 */
package com.example.cargotracker.shipper.infrastructure.repositories;
