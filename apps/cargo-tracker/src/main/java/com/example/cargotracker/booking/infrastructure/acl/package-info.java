/**
 * 他の BC が定義した ACL ポートの実装（アダプタ）。
 *
 * <p><strong>翻訳する場所である。</strong> 受け渡すのは素の値であり、
 * 相手の BC の型をここで組み立てない。組み立てると相手のドメインを
 * 直接参照することになり、ACL を置いた動機が消える（ArchUnit ルール 4）。
 */
package com.example.cargotracker.booking.infrastructure.acl;
