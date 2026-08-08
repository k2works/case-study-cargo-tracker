/**
 * 予約コンテキストが購読するドメインイベント。
 *
 * <p>他の BC が発行した「起きた事実」を受け取り、予約のことばへ翻訳して
 * 自分のモデルを進める。<strong>購読は AFTER_COMMIT である</strong>（ADR-009）。
 */
package com.example.cargotracker.booking.interfaces.events;
