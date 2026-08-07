/**
 * 予約コンテキストの読み取り（CQRS のクエリ側）。
 *
 * <p>画面が判断を持たないよう、<strong>表示名・バッジ・操作の可否まで決めて渡す</strong>。
 * 実装はインフラ層に置く（ArchUnit ルール 3）。
 */
package com.example.cargotracker.booking.application.internal.queryservices;
