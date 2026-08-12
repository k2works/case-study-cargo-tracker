/**
 * 請求コンテキストの画面（Thymeleaf + htmx）。
 *
 * <p>経理担当者（{@code ROLE_BILLING}）だけが開ける。
 *
 * <p><strong>画面は判断を持たない。</strong> 出し分けは集約の述語か application 層の
 * 規則をそのまま呼ぶ（ADR-022）。画面で状態名を比べると、同じ規則が画面の数だけ散る。
 */
package com.example.cargotracker.billing.interfaces.web;
