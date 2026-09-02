/**
 * 予約コンテキスト（bookingms）。荷主管理・貨物予約・旅程管理・見積・状態遷移・キャンセル承認を責務とする。
 *
 * <p>本パッケージは境界付けられたコンテキストのルートである。マイクロサービスと BC は
 * 1 対 1 に対応し、独立した Spring Boot アプリケーションとしてデプロイする（ADR-001）。
 *
 * <p><strong>他のサービスのクラスを直接参照してはならない。</strong> 連携は HTTP または
 * メッセージング経由であり、共有してよいのは共有カーネル（{@code com.example.shared}）に
 * 限る。この前提は ArchUnit の {@code serviceIsolationRule} が検証する。
 *
 * <p>層の構成は domain → application → infrastructure / interfaces の順に外へ向かう。
 * 依存は常に外から内へ向かい、domain はどの層にもフレームワークにも依存しない。
 */
package com.example.bookingms;
