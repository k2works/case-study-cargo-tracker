---
title: イテレーション 6 ふりかえり（KPT）
description: IT6（追跡照会 US18・公開追跡ページ・30 秒ポーリング・遅延例外 US19・破損/紛失例外 US20・負債返済 T28/T29/T32・Phase 3 完了）の KPT と次イテレーション引き継ぎ
date: 2026-07-29T00:00:00.000Z
---

# イテレーション 6 ふりかえり（KPT）

- **対象**: IT6（追跡情報照会 US18・公開追跡ページ・Turbo Frame 30 秒差分ポーリング・遅延例外 US19・破損/紛失例外 US20・Tracking Context 例外処理確立・Phase 3 後半）
- **期間**: Week 11-12（〜2026-07-29 クローズ）
- **実績**: 15 SP 完了（目標 15 SP・達成率 100%・Phase 3 完了）／ RSpec 337 examples 0 failures ／ 全体カバレッジ 95.38%・新規 92.7% ／ SonarQube Quality Gate PASS ／ Release 0.3 は push・タグ発行の外部影響を伴うため保留

## Keep（うまくいったこと）

- **中盤インサイドアウトで例外処理を貫通**。`ExceptionType`・`TrackingExceptionEvent`（集約内エンティティ）・`TrackingStatus.EXCEPTION`・`register/resolve_exception`（precondition 付き）をドメイン層から作り込み、永続化 → アプリ層 → 通知 → UI → ナビ導線まで貧血に陥らせず貫通できた。
- **負債返済枠を序盤で全消化**。T28（荷役二重登録防止・冪等ガード）・T29（楽観ロック競合回帰テスト）・T30（状態機械 precondition の DoD 化）・T31（UI 挙動 DoD）・T32（MISROUTED→routing_status 反映）を IT5 引き継ぎどおり消化。[[feedback_debt-allowance-defer-antipattern]] の再発回避を継続。
- **設計反映 7 点を実装と同時に正典へ同期**。[[feedback_scope-change-canon-sync]] に従い data-model・architecture_backend・domain-model・ui_design を実装コミットと近接して更新し、先行乖離を防いだ。
- **クローズ前レビューで高優先度 5 件を全対応**。5 視点レビューで programmer と architect が独立に同一の高指摘（発生前状態の永続化不足）へ収束したことで確度の高い欠陥を早期是正できた。tester の受入基準テスト漏れ（通知系）も 4 件即対応。
- **BC 独立性の維持**。T32 の MISROUTED 反映をイベント経由で Booking BC 内に閉じ、Packwerk privacy ゼロ違反を維持した。

## Problem（課題・負債の発生源）

- **状態機械の永続化ギャップを実装時に見落とした**。`status_before_exception` を集約メモリのみで保持し永続化しなかったため、ユニットテストは緑でもクロスリクエスト（US17 手動更新→例外→解決）で誤復帰する偽の安全網になっていた。レビューで初めて検出（H1）。ドメイン集約の状態を永続化アダプタで再導出（履歴推測）した設計判断が根本原因。
- **受入基準の通知テストが代表値のみだった**。US19/US20 の「荷主通知」を DELAY のみ、escalation を LOST のみで代表し、EXCEPTION_RESOLVED・DAMAGE/LOST 荷主通知・escalation 負ケースが穴になっていた（tester H1-H4）。受入基準とテストの 1:1 対応の取りこぼし。
- **冪等ガードが DB 制約の裏付けを欠く**。T28 は read-then-write のアプリ層チェックのみで、並行 POST に対する TOCTOU の余地が残る（unique index が非 unique）。
- **例外解決のセマンティクスが未精査**。「対応報告＝発生前状態へ復帰」は遅延には合うが、紛失（LOST）では復帰が業務的に不自然（user-rep H2）。domain-model のビジネスルール 5 をそのまま実装したが業務妥当性の検討が浅かった。

## Try（次イテレーションの改善アクション）

| # | アクション | 期待効果 | 担当/時期 |
|:--|:--|:--|:--|
| T33 | ドメイン集約の内部状態は永続化アダプタで再導出せず必ずカラムで永続化する設計ルールを明文化（状態の再導出禁止） | 偽の安全網（ローカル緑・実挙動乖離）の再発防止 | IT7 計画時 |
| T34 | 受入基準の「通知送信」項目は正・負の同値クラス（送る/送らない）を必ずテスト化する DoD を追加 | 通知系の受入基準テスト漏れの機械的検出 | IT7 |
| T35 | 荷役冪等キーに DB unique index を張り `RecordNotUnique` を捕捉（アプリ層チェックは保険に残す） | 並行 POST の TOCTOU に対する最終防衛 | IT7 序盤 |
| T36 | 例外解決のセマンティクス見直し（LOST は復帰でなく補償完了として別状態・CLAIMED 等終端での例外登録 precondition） | 業務実態と状態機械の整合 | IT7（設計トピック） |
| T37 | 遅延対応の新到着予定日を構造化し公開追跡の推定到着日へ反映（現状 resolution_notes 自由テキスト） | 荷主が見る推定到着日と対応内容の整合 | IT7 |
| 将来 | ポーリングの条件付き GET（ETag/304 差分）実装・エスカレーション通知の Outbox 化（partial-apply 解消） | 差分最適化・重大例外の確実な到達 | Phase 4 以降 |

## SonarQube 品質ゲート

| 指標 | 結果 | 目標 | 判定 |
|:--|:--|:--|:--|
| Quality Gate | PASS | PASS | ✅ |
| 新規コードカバレッジ | 92.7% | 80% 以上 | ✅ |
| 重複率 | 0.0% | 3% 未満 | ✅ |
| 違反（Violation） | 0 | 0 | ✅ |

補助的な静的解析も全てクリーン（rubocop 0 / packwerk 0 / brakeman 0 / bundler-audit 0）。全体カバレッジ 95.38%。

## 次イテレーション（IT7）への引き継ぎ

- **持ち越し**: T33（状態の再導出禁止ルール）・T34（通知の正負同値テスト DoD）・T35（荷役冪等 DB unique index）・T36（例外解決セマンティクス・precondition 拡充）・T37（新到着予定日の構造化）・将来（ETag/304・Outbox）。
- **IT7 の位置づけ**: [release_plan.md](release_plan.md) の通り Phase 4（見積・料金計算・精算）＝US01/US21/US22/US23。[development_strategy.md](development_strategy.md) では IT7 を**終盤（アウトサイドイン）**と位置づけ、既存集約（Booking/Routing/Shipper/Billing）を業務シナリオ起点で結合する局面に移る。
- **Release**: Phase 3 完了により **Release 0.3** を発行可能な状態（DoD は Release 発行以外すべて達成）。push・タグ発行は `developing-release` で別途実施する。

## 更新履歴

| 日付 | 版 | 変更内容 | 担当 |
|:--|:--|:--|:--|
| 2026-07-29 | 初版 | IT6 ふりかえり（KPT）を作成 | 開発チーム |
