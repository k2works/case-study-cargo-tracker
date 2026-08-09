/**
 * 荷役コンテキストが提供する ACL アダプタ。
 *
 * <p>他の BC が定義した出力ポートを、荷役のことばに翻訳して実装する。
 * <strong>境界では素の値だけを受け渡す。</strong> 相手の BC に荷役の型を
 * 見せると、その BC が荷役のドメインを参照することになる（ArchUnit ルール 4）。
 */
package com.example.cargotracker.handling.infrastructure.acl;
