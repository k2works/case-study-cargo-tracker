---
title: イテレーション 5 完了報告書
description: IT5（US10 経路条件調整・US11 経路情報可視化・US12 確定経路通知・経路設計ナビ導線）の完了報告書。
tags: development, iteration-report, iteration-5, go
---

# イテレーション 5 完了報告書

## エグゼクティブサマリー

IT5 は中盤局面（インサイドアウト）として、経路条件調整（US10・3SP）・経路情報の可視化補完（US11・2SP）・確定経路通知（US12・2SP）を計画どおり 7 SP 完遂し、IT4 で作り込んだ経路探索・経路確定フローを運用完成させた。加えて、経路設計者がダッシュボードから経路設計作業に到達できない導線欠落（ユーザー報告）を /route-design の新設で解消した。BC 独立性の要である NotificationPort は booking/application に配置し（opening の validating-design 是正どおり）、`.go-arch-lint.yml` 無改変で BC 独立性を維持。品質ゲート（make check・CI・SonarQube Quality Gate）はすべて PASS。

## 達成状況

| ストーリー | SP | 状態 |
|-----------|----|----|
| US10 経路条件を調整して再算出する | 3 | ✅ 完了 |
| US11 経路情報を予約に紐付ける | 2 | ✅ 完了（可視化補完） |
| US12 確定経路を荷主に通知する | 2 | ✅ 完了 |
| **合計** | **7** | **100%** |

追加対応: 経路設計者の作業導線 /route-design（ナビ「経路設計」）。

### 成功基準

- ✅ US10/US11/US12 の受け入れ基準を満たす（条件確認・調整再算出・協議依頼・紐付け可視化・通知送信・記録）。
- ✅ 条件調整（RouteAdjustment・期限オーバーライド）を domain/application で隔離検証。
- ✅ Notification 値オブジェクト・NotificationPort（booking/application）で送信を抽象化。
- ✅ ドメイン層カバレッジ 90% 以上（booking 97.5%）、SonarQube Quality Gate PASS。
- ✅ `make check` green・CI success。

## 技術的成果

### 実装

- **US10 経路条件調整**: `RouteAdjustment`（期限オーバーライド）で `SearchRoutesService` を再駆動する `AssignRouteService.CandidatesWithAdjustment/AssignWithAdjustment`。候補ゼロ時の `RequestNegotiationService`（EventPublisher で協議依頼イベント）。確定済み経路の再調整 `Cargo.MarkMisrouted` + `Readjust`（ROUTED→MISROUTED→再確定）。`/route` に条件調整フォーム・`/route/negotiate`・`/route/readjust`。
- **US11 可視化補完**: IT4 US09 の紐付けを前提に、`CargoListItem` に RoutingStatus を追加し予約一覧に経路状態列（CQRS クエリ側）。二重の紐付けコマンドは追加せず。
- **US12 確定経路通知**: `Notification` 値オブジェクト・`Cargo.BuildRouteNotificationContent`（経由港・所要日数・到着予定日・料金概算）。`NotificationPort`（booking/application・BC 独立）・`NotificationRepository`・`NotifyRouteService`。`notification` テーブル(000011)・`/bookings/{id}/notify`（プレビュー・送信・記録）・loggingNotifier。
- **ナビ導線**: 経路設計作業一覧 `/route-design`（経路提案中の予約→経路割り当て）・navbar「経路設計」。

### コード規模

- 変更ファイル: 38（Go・SQL・テンプレート・E2E・docs）
- Go コード: 約 +1,448 行 / -45 行
- 新規マイグレーション: 000011（notification）
- ADR: ADR-0003 を更新（MISROUTED の 2 系統）

## 品質指標

| 指標 | 実績 |
|------|------|
| ドメイン層カバレッジ | booking 97.5% / routing 96.6% / estimation 91.5% / shared 95.8% |
| SonarQube Quality Gate | PASS（new_coverage 81.2% / 重複 0.4% / new_violations 0 / Bug 0 / Vulnerability 0） |
| make check | green（build + test + lint + arch） |
| golangci-lint | 0 issues |
| go-arch-lint | OK（BC 独立性維持・無改変） |
| govulncheck | 脆弱性なし |
| CI（Backend CI・go/take-1） | success |
| テスト | 単体・統合（testcontainers）・E2E（Playwright 7 本）すべて green |

## レビュー結果

XP 5 視点のマルチパースペクティブレビューを 2 段（開発中の中間 self-review 3 観点 + クローズ時 5 観点統合）で実施（[IT5 レビュー](../review/it5_go_review_20260725.md)）。

- **クローズ前に対応**: MISROUTED の実フロー配線（dead code 解消）、期限ちょうど境界バグ、ui_design のストーリー ID 衝突是正、US10 見出しの適正化、通貨表記統一、二重送信抑止（部分）、協議依頼理由の編集可、ADR-0003 追記。
- **繰越（IT6）**: 協議依頼ワークリスト（業務ループ・user-rep H-1）・通知待ちワークリスト・US10 の経由地/種別調整・通知成功フラッシュ・実 notifier の outbox・sqlcgen 重複返済。

## 課題と残作業

- **業務ワークリストの欠如（IT6）**: 協議依頼（US10）・通知待ち（US12）の営業側受信導線がなく、業務ループが閉じない。IT6 でワークリストを実装。
- **US10 の調整軸**: 現状は到着期限のみ。経由地追加・貨物種別変更は後続。
- **技術的負債**: sqlcgen 全スキーマ重複（IT4 からの継続）・実 notifier の outbox（外部連携時）。
- **プロセス改善（ふりかえり Try）**: ロール別「作業入口」の DoD 化（ナビ導線欠落の再発防止）。

## 次イテレーション（IT6）への引き継ぎ

- IT6 スコープ: US14 追跡番号発行・US15 荷役記録・US16 引取記録・US18 追跡照会（Release 0.2 完了・Phase 2 完了）。
- 運用完成の持ち越し: 協議依頼/通知待ちワークリスト（US10-12 の業務ループ）。
- 良好な状態の維持: BC 独立性（go-arch-lint 無改変）・ドメイン層 90%+ カバレッジ・NotificationPort の application 配置・2 段レビュー運用。

## 関連ドキュメント

- [IT5 イテレーション計画](iteration_plan-5.md)
- [IT5 ふりかえり](retrospective-5.md)
- [IT5 マルチパースペクティブレビュー](../review/it5_go_review_20260725.md)
- [ADR-0003 TransportStatus/RoutingStatus 正典](../adr/0003-transport-status-canon.md)
- [リリース計画](release_plan.md)
