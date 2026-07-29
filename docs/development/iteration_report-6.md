---
title: イテレーション 6 完了報告書
description: IT6（追跡照会 US18・公開追跡ページ・30 秒ポーリング・遅延例外 US19・破損/紛失例外 US20・負債返済 T28/T29/T32・Phase 3 完了）の成果・品質指標・課題
date: 2026-07-29T00:00:00.000Z
---

# イテレーション 6 完了報告書

## エグゼクティブサマリー

Phase 3（追跡・荷役・例外処理）の後半として、追跡情報照会（US18）・遅延例外処理（US19）・破損/紛失例外処理（US20）を中盤インサイドアウトの TDD で完成させました。Tracking Context に例外処理（`TrackingExceptionEvent` 集約内エンティティ・`ExceptionType`・`TrackingStatus.EXCEPTION`・`tracking_exception_detected`/`resolved` イベント）を確立し、荷主通知・紛失時の管理職エスカレーション・対応報告を通知基盤（ADR-0002）へ結線しました。US18 では認証不要の公開追跡ページ（個人情報非表示・追跡イベント履歴・推定到着日）と追跡詳細の 30 秒 Turbo Frame 差分ポーリングを実装しました。あわせて IT5 から引き継いだ技術的負債（T28 荷役二重登録防止・T29 楽観ロック競合回帰・T30 状態機械 precondition・T31 UI 挙動 DoD・T32 MISROUTED→routing_status）を全消化しました。計画 15 SP を 100% 消化し、RSpec 337 examples 0 failures・全体カバレッジ 95.38%・SonarQube Quality Gate PASS を達成。マルチパースペクティブレビュー（5 視点）の高優先 5 件をクローズ前に全対応しました。Phase 3 を完了し Release 0.3 発行可能な状態としましたが、push・タグ発行は外部影響を伴うため保留しています。

## 達成状況

| US | 概要 | SP | 状態 |
|:---|:-----|:--|:-----|
| US18 | 追跡情報を照会する（公開追跡ページ・30 秒ポーリング・推定到着日・イベント履歴） | 5 | 完了 |
| US19 | 遅延例外を処理する（EXCEPTION 遷移・荷主通知・対応報告） | 5 | 完了 |
| US20 | 破損・紛失例外を処理する（紛失時の管理職エスカレーション） | 5 | 完了 |
| **計** | | **15** | **100%（実績 15 SP）** |

## 技術的成果

- **Tracking Context 例外処理の確立**: `ExceptionType`（DELAY/DAMAGE/LOST/CUSTOMS_HOLD・LOST は `escalation_required?`）・`TrackingExceptionEvent`（集約内エンティティ）・`TrackingStatus.EXCEPTION`・`TrackingActivity#register_exception`/`resolve_exception` を PORO ドメインとして実装。例外発生前の状態を集約状態として永続化し、対応報告での正確な復帰を保証（precondition・T30）。
- **例外通知のイベントコレオグラフィ**: `tracking_exception_detected`（荷主通知・紛失時 MANAGER エスカレーション）・`tracking_exception_resolved`（対応報告通知）をアプリケーションサービスがコミット後に発行し、既存 `NotificationSubscribers` へ結線（ADR-0002・`install_once` 冪等ガード・購読側例外非伝播）。
- **US18 追跡照会**: 認証不要の公開追跡ページ（`/public/tracking`・輸送状態/現在地/推定到着日/イベント履歴・個人情報非表示）と追跡詳細の 30 秒 Turbo Frame 差分ポーリング（`status` エンドポイント・`polling_controller`・aria-live）。推定到着日は確定経路 `CargoItinerary` の最終 leg 到着時刻から導出。
- **負債返済**: T28（冪等キーによる荷役二重登録防止）・T29（楽観ロック競合回帰テスト）・T32（旅程外荷役の MISROUTED を `cargos.routing_status` へイベント経由で反映）。
- **BC 独立性**: Tracking/Handling が Booking の公開 API・ADR-0003 越境識別子・ドメインイベント経由のみで連携（Packwerk privacy ゼロ違反）。

## 品質指標

| 指標 | 結果 | 目標 | 判定 |
|:--|:--|:--|:--|
| RSpec | 337 examples 0 failures | 全 green | ✅ |
| 全体カバレッジ（Line） | 95.38% | 80% 以上 | ✅ |
| 新規コードカバレッジ | 92.7% | 80% 以上 | ✅ |
| SonarQube Quality Gate | PASS | PASS | ✅ |
| 重複率 | 0.0% | 3% 未満 | ✅ |
| RuboCop / Brakeman / bundler-audit / Packwerk | 0 / 0 / 0 / 0 | 0 | ✅ |

## レビュー結果

マルチパースペクティブレビュー（5 視点）を実施。高優先度 5 件をすべてクローズ前に対応:

- **H1（programmer + architect 収束）**: `status_before_exception` の永続化不足 → 集約状態としてカラム永続化・履歴再導出を廃止。
- **H2/H3/H4（tester）**: 対応報告 HTTP フロー・解決/破損/紛失の荷主通知・escalation 負ケースのテスト漏れ → テスト追加。
- **H5（user-representative）**: 公開追跡ページの追跡イベント履歴欠落 → 時系列表示を追加。

中・低優先度の一部（荷役冪等の DB unique index・例外解決セマンティクス・新到着予定日の構造化・ETag/304 差分・Outbox 化）は IT7 へ引き継ぎ。詳細は [IT6 実装レビュー](../review/IT6実装_review_20260729.md)。

## 課題と残作業

- **Release 0.3 未発行**: DoD は Release 発行を除きすべて達成。push・タグ発行は外部影響を伴うため保留し、`developing-release` で別途実施する。
- **CI 未実行**: IT6 のコミットは未 push（CI は workflow_dispatch）。ローカル CI 相当チェックは全て緑。
- **例外解決のセマンティクス（T36）**: LOST は「発生前状態へ復帰」が業務的に不自然。IT7 で見直し。
- **荷役冪等の DB 制約（T35）**: アプリ層チェックのみ。unique index による最終防衛を IT7 で追加。

## 次イテレーション（IT7）引き継ぎ

- 持ち越しストーリーなし（IT6 スコープ完了・Phase 3 完了）。
- IT7 は Phase 4（見積・料金計算・精算＝US01/US21/US22/US23）・**終盤アウトサイドイン**（development_strategy）。
- レビュー引き継ぎ: T33（状態の再導出禁止ルール）・T34（通知の正負同値テスト DoD）・T35（荷役冪等 DB unique index）・T36（例外解決セマンティクス・precondition 拡充）・T37（新到着予定日の構造化）。
- Release 0.3 は `developing-release` で発行する。

## 関連ドキュメント

- [イテレーション 6 計画](iteration_plan-6.md)
- [イテレーション 6 ふりかえり](retrospective-6.md)
- [IT6 実装レビュー](../review/IT6実装_review_20260729.md)
- [ドメインモデル](../design/domain-model.md)（Tracking Context 例外処理）
- [データモデル](../design/data-model.md)（tracking_exception_events）
- [ADR-0002 ドメインイベントによる通知基盤](../adr/0002-domain-events-and-notification.md)
- [ADR-0003 越境識別子・ACL](../adr/0003-cross-context-identifier-and-acl.md)
- [リリース計画](release_plan.md)

## 更新履歴

| 日付 | 版 | 変更内容 | 担当 |
|:-----|:---|:---------|:-----|
| 2026-07-29 | 初版 | IT6 完了報告書を作成 | 開発チーム |
