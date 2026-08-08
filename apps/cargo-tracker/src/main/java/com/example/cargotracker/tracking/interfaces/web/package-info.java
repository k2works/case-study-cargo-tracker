/**
 * 追跡の画面（Thymeleaf）。
 *
 * <p>2 つの入口を持つ。
 *
 * <ul>
 *   <li>{@link com.example.cargotracker.tracking.interfaces.web.TrackingController}
 *       — 要認証。荷主・荷受人・追跡管理者が使う（US18）</li>
 *   <li>{@link com.example.cargotracker.tracking.interfaces.web.PublicTrackingController}
 *       — 認証不要。荷主が取引先へ URL を転送する前提の画面（US18）</li>
 * </ul>
 *
 * <p><strong>公開画面は Tracking Context に置く。</strong> IT6 までは
 * {@code shared} の {@code HomeController} が返しており、
 * <strong>追跡の入口を探す人がそこを見ない</strong>形になっていた。
 *
 * <p>両者は同じ {@code TrackingInquiryService} を使う。
 * <strong>見せる範囲を画面ごとに変えない。</strong> 変えると、片方にだけ
 * 個人情報が混ざる形が生まれる。
 */
package com.example.cargotracker.tracking.interfaces.web;
