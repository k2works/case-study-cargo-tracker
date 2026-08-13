/**
 * Spring Security の構成と認証イベントの処理。
 *
 * <p>URL 単位の認可規則・ログインフォーム・ログアウトの構成を持つ。
 * ヘルスチェックは横断的な防御の対象外にする。過負荷時に liveness が 401/503 を返すと
 * 再起動ループに入るためである。
 *
 * <p>認証イベントの購読により失敗回数とロック状態を更新する。<strong>ロック中の試行では
 * 失敗回数を増やさない。</strong> 増やすとロックが際限なく延長され、正当な利用者が復帰できなくなる。
 */
package com.example.cargotracker.security.infrastructure.config;
