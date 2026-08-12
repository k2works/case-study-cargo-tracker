/**
 * 荷主コンテキストが外へ問い合わせる約束（ACL ポート）。
 *
 * <p><strong>ポートを定義するのは利用する側である</strong>（ADR-005 / ADR-007）。
 * 実装は提供側の BC の {@code infrastructure/acl} に置く。
 *
 * <p>利用者アカウントとの紐付け（{@code LinkedAccounts}）を、Security の型を知らずに扱う。
 */
package com.example.cargotracker.shipper.application.internal.outboundservices.acl;
