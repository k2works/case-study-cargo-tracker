/**
 * 認証コンテキストの永続化アダプタ（MyBatis）。
 *
 * <p>{@code application.port} で宣言した出力ポートの実装である。集約と DB のレコードの
 * 変換はここに閉じ込め、ドメインモデルに DB の都合を持ち込まない。
 *
 * <p><strong>本パッケージのテストは Testcontainers の実 PostgreSQL で書く。</strong>
 * 方言差は両方向に起きるため、全クエリが両方の DB で解釈できることを方言スモークでも確かめる。
 */
package com.example.authms.infrastructure.persistence;
