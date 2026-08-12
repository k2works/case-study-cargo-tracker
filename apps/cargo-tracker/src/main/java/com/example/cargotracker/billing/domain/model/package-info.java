/**
 * 請求コンテキストの集約・エンティティ・値オブジェクト。
 *
 * <p>集約ルートは {@code Invoice} である。請求書は<strong>金額の確定と入金の記録を
 * 1 つの一貫性の境界で守る</strong> —— 確定していない金額に入金は付かず、
 * 入金済みの請求書の金額は変わらない。
 *
 * <p><strong>金額は丸めた値を持つ</strong>（ADR-016）。再計算で導出しない。
 * 発行済み請求書の金額は、税率が変わっても変わってはならない。
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。予約 ID は
 * Booking の {@code BookingId} ではなく {@code BillingBookingId} を、荷主 ID は
 * {@code BillingShipperId} を使う。<strong>同じ番号を指していても、
 * 型を分けることで越境をコンパイラが止める。</strong>
 */
package com.example.cargotracker.billing.domain.model;
