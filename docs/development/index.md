# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### 開発戦略

| ドキュメント | 説明 |
|-------------|------|
| [開発戦略](development_strategy.md) | 序盤アウトサイドイン / 中盤インサイドアウト / 終盤アウトサイドインの 3 局面戦略。ウォーキングスケルトン基盤と IT ごとの設計整合 |

### リリース計画

| ドキュメント | 説明 |
|-------------|------|
| [リリース計画](release_plan.md) | 全 26 US・85 SP・7+1 イテレーション。ストーリー × イテレーション対応の Single Source of Truth |

### イテレーション計画

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|-----------|-----------|------|
| IT1 | [iteration_plan-1.md](iteration_plan-1.md) | [retrospective-1.md](retrospective-1.md) | [iteration_report-1.md](iteration_report-1.md) | 完了 |
| IT2 | [iteration_plan-2.md](iteration_plan-2.md) | [retrospective-2.md](retrospective-2.md) | [iteration_report-2.md](iteration_report-2.md) | 完了 |
| IT3 | [iteration_plan-3.md](iteration_plan-3.md) | [retrospective-3.md](retrospective-3.md) | [iteration_report-3.md](iteration_report-3.md) | 開発完了 |
| IT4 | [iteration_plan-4.md](iteration_plan-4.md) | [retrospective-4.md](retrospective-4.md) | [iteration_report-4.md](iteration_report-4.md) | 開発完了 |
| IT5 | [iteration_plan-5.md](iteration_plan-5.md) | [retrospective-5.md](retrospective-5.md) | [iteration_report-5.md](iteration_report-5.md) | 開発完了 |
| IT6 | [iteration_plan-6.md](iteration_plan-6.md) | [retrospective-6.md](retrospective-6.md) | [iteration_report-6.md](iteration_report-6.md) | 開発完了 |

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 13 | 13 | 100% |
| IT2 | 10 | 10 | 100% |
| IT3 | 14 | 14 | 100% |
| IT4 | 12 | 12 | 100% |
| IT5 | 17 | 17 | 100% |
| IT6 | 6 | 6 | 100% |
| **累計** | **72** | **72** | **100%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|
| Release 1.0 MVP（Phase 1・IT1-5） | [release_report-1.0.md](release_report-1.0.md) | 開発完了 |

### 開発ジャーナル

日々のセッションで実行した Skill、判断の経緯、得られた学びを記録します。

| 日付 | ジャーナル | 概要 |
|------|-----------|------|
| 2026-07-04 | [20260704](../journal/20260704.md) | 分析: C# 版設計 10 件・レビュー・ADR 3 件 / 運用: スキャフォールド・Heroku デプロイ・Nix CI/CD |
| 2026-07-08 | [20260708](../journal/20260708.md) | 開発: 開発戦略策定・IT1 実装（US26/02/03/01・74テスト）・XP レビュー高優先5件解消・ADR 0004/0005・E2E 導入 |
| 2026-07-09 | [20260709](../journal/20260709.md) | IT2+IT3 完走: 計画→検証→Codex 分業 TDD（US04-06/US24-25/07/08・170テスト）→負債返済（M1/ADR-0006/0007）→ナビ整合性制度化・ログイン UX・router シード→XP レビュー |
| 2026-07-13 | [20260713](../journal/20260713.md) | IT4+IT5 完走・Release 1.0 MVP 出荷: 計画→検証→Ralph Loop TDD（US09-18・235テスト）→BC 連携 ACL・イベント状態同期→BookingStatus 永続化バグ是正→SonarQube 導入→実 MediatR E2E 補完→XP レビュー |

## 補足

- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
