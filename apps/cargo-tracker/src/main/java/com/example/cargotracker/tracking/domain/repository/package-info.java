/**
 * 追跡コンテキストのリポジトリ（出力ポート）。
 *
 * <p><strong>interface だけを置く。</strong> 実装は
 * {@code infrastructure/repositories} にあり、依存の向きはここへ向かう（DIP）。
 * 実装をここに置くと、ドメインが永続化の都合を知ることになる。
 */
package com.example.cargotracker.tracking.domain.repository;
