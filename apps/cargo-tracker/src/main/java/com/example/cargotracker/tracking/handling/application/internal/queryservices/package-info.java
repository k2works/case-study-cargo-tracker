/**
 * 荷役モジュールのクエリサービス（CQRS の読み取り側）。
 *
 * <p>画面はここを経由して読む。<strong>画面がリポジトリを直接参照しない</strong>
 * （ArchUnit ルール 3）。読み取りの都合で集約の形を変えないための境界である。
 */
package com.example.cargotracker.tracking.handling.application.internal.queryservices;
