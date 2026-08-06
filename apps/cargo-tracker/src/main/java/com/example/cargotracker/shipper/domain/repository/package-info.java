/**
 * 荷主コンテキストの出力ポート。
 *
 * <p>ドメイン層でインターフェースを定義し、実装は {@code infrastructure.repositories} に置く（DIP）。
 * アプリケーション層はこのポート経由でのみ永続化に触れる（test_strategy.md §3.3 ルール 3）。
 */
package com.example.cargotracker.shipper.domain.repository;
