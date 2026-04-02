/**
 * ルート検索コンテキスト（routing Bounded Context）。
 *
 * <p>予約の輸送条件（出発地・目的地・希望着日・貨物種別・重量）をもとに
 * 外部航路システムへ問い合わせ、利用可能なルート候補を返す責務を持つ。
 *
 * <h2>主要コンポーネント</h2>
 * <ul>
 *   <li>{@code RouteSearchService} — ルート検索のコアサービス。
 *       予約 ID 起点と直接条件指定の 2 モードをサポートする。
 *       {@code RouteProviderPort} の実装が登録されている場合のみ有効（条件付き Bean）。</li>
 *   <li>{@code BookingQueryPort} — booking コンテキストから予約情報を取得するための
 *       アウトバウンドポート（ACL）。実装は booking 側の
 *       {@code BookingQueryPortAdapter} が提供する。</li>
 *   <li>{@code RouteProviderPort} — 外部ルートプロバイダーへのアウトバウンドポート。
 *       本番（product プロファイル）では外部 REST API と接続し、
 *       それ以外ではスタブ実装が使用される。</li>
 * </ul>
 *
 * <h2>貨物種別の命名について</h2>
 * <p>routing コンテキストの {@code CargoType} は外部航路システムの用語に準拠しており、
 * booking/quote コンテキストとは異なる名称を使用する
 * （例: {@code DANGEROUS_GOODS} → {@code HAZARDOUS}）。
 * 変換は {@code BookingQueryPortAdapter}（ACL）が一元管理する。
 */
package com.example.cargotracker.routing;
