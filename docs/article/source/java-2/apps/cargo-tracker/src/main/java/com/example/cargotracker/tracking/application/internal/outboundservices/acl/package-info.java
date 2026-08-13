/**
 * 追跡コンテキストが外へ問い合わせる約束（ACL ポート）。
 *
 * <p><strong>ポートを定義するのは利用する側である</strong>（ADR-005 / ADR-007）。
 * 荷主の連絡先・通関の状態・港名を、相手の値オブジェクトを知らずに素の値で受け取る。
 *
 * <p><strong>運ぶのは事実だけである。</strong> 「誰に何と伝えるか」は受け取った側が決める。
 */
package com.example.cargotracker.tracking.application.internal.outboundservices.acl;
