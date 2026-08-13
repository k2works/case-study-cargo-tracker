/**
 * 追跡コンテキストの読み取り（CQRS のクエリ側）。
 *
 * <p>貨物追跡の照会と、例外（遅延・破損・紛失・誤配）の一覧を組み立てる。
 *
 * <p><strong>公開追跡と社内の照会は別の入口である。</strong> 公開側には
 * 引取確認コードのような、取引先へ転送される情報を渡さない。
 */
package com.example.cargotracker.tracking.application.internal.queryservices;
