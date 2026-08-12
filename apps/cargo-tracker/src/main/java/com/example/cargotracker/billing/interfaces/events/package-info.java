/**
 * 請求コンテキストが受け取るドメインイベントの入口。
 *
 * <p><strong>購読は {@code AFTER_COMMIT} で行う</strong>（ADR-009）。発生元の取引が
 * 確定してから動く —— 巻き戻った出来事に反応して請求書を作らないためである。
 *
 * <p>キャンセルの承認（{@code CargoCancelledEvent}）からキャンセル料の請求書を作る。
 */
package com.example.cargotracker.billing.interfaces.events;
