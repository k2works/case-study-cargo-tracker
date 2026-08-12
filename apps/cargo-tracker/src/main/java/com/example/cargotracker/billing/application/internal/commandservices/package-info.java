/**
 * 請求コンテキストの状態を変える操作。
 *
 * <p>料金の算出・確定、請求書の発行、入金の確認、督促の記録を扱う。
 * <strong>業務の判断は集約が持ち、ここは順番と取引の境界を決める。</strong>
 *
 * <p><strong>他の BC へは ACL ポートかドメインイベントで伝える</strong>（ADR-009 / ADR-012）。
 */
package com.example.cargotracker.billing.application.internal.commandservices;
