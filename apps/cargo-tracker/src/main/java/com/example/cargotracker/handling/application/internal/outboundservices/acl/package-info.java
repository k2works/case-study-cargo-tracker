/**
 * 荷役モジュールから他 BC への出力ポート（ACL）。
 *
 * <p>ここに置くのは<strong>interface だけ</strong>である。実装は相手側の BC の
 * {@code infrastructure/acl} が持つ。荷役が Booking の型を知らずに
 * 予定ルートを参照できるのは、この境界で素の値に落としているためである。
 */
package com.example.cargotracker.handling.application.internal.outboundservices.acl;
