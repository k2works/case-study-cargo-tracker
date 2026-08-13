/**
 * 追跡コンテキストが提供する ACL アダプタ。
 *
 * <p>他の BC が定義した出力ポートを、追跡のことばに翻訳して実装する。
 * <strong>境界では素の値だけを受け渡す。</strong> 相手の BC に追跡の型を
 * 見せると、その BC が追跡のドメインを参照することになる（ArchUnit ルール 4）。
 */
package com.example.cargotracker.tracking.infrastructure.acl;
