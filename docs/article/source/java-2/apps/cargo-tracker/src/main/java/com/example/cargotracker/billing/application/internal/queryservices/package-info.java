/**
 * 請求コンテキストの読み取り（CQRS のクエリ側）。
 *
 * <p>画面に出す形（{@code *View}）と、その組み立ての規則を置く。
 * <strong>規則はここ、問い合わせは infrastructure である</strong>（ADR-022）。
 *
 * <p>集約を経由しない。一覧のたびに集約を復元すると、行数に比例して重くなる。
 */
package com.example.cargotracker.billing.application.internal.queryservices;
