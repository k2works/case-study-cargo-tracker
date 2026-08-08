/**
 * 追跡コンテキストの永続化（MyBatis）。
 *
 * <p>ドメインのリポジトリ interface を実装する出力アダプタである。
 * <strong>UUID の型ハンドラは各マッパーで明示する。</strong> 実行時は設定で
 * 解決されるが、設定を読まない道具（JIG）からは解決できず、
 * そのマッパーだけが読み飛ばされる（IT5 の P5）。
 */
package com.example.cargotracker.tracking.infrastructure.repositories;
