# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画

| ドキュメント | 説明 |
|-------------|------|
| [リリース計画](./release_plan.md) | リリース全体のスコープ、スケジュール、ベロシティ、バッファ戦略（145 SP / 10 イテレーション） |
| [開発戦略](./development_strategy.md) | 局面別 TDD アプローチ、ウォーキングスケルトンの定義、設計整合方針 |

### イテレーション計画

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|-----------|-----------|------|
| IT1 | [計画](./iteration_plan-1.md) | [ふりかえり](./retrospective-1.md) | [完了報告書](./iteration_report-1.md) | **完了**（16/16 SP） |
| IT2 | [計画](./iteration_plan-2.md) | [ふりかえり](./retrospective-2.md) | [完了報告書](./iteration_report-2.md) | **完了**（17/17 SP） |
| IT3 | [計画](./iteration_plan-3.md) | [ふりかえり](./retrospective-3.md) | [完了報告書](./iteration_report-3.md) | **完了**（14/15 SP） |
| IT4 | [計画](./iteration_plan-4.md) | [ふりかえり](./retrospective-4.md) | [完了報告書](./iteration_report-4.md) | **完了**（12/14 SP） |
| IT5 | [計画](./iteration_plan-5.md) | [ふりかえり](./retrospective-5.md) | [完了報告書](./iteration_report-5.md) | **完了**（11/11 SP・縮退 0 件） |
| IT6 | [計画](./iteration_plan-6.md) | [ふりかえり](./retrospective-6.md) | [完了報告書](./iteration_report-6.md) | 完了（11/11 SP） |

イテレーション開始時に行を追加します。

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 16 | 16 | 100% |
| IT2 | 17 | 17 | 100% |
| IT3 | 15 | 14 | 93% |
| IT4 | 14 | 12 | 86% |
| IT5 | 11 | 11 | 100% |
| **累計** | **148** | **59** | **40%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|
| Phase 1 | 基盤とウォーキングスケルトン（IT1-2） | 31 | 33 | **完了**（TS05a 2 SP を IT3 から前倒し） |
| Phase 2 | 認証と CI/CD（IT3） | 15 | 14 | **完了**（TS05b の一部を IT4 へ） |
| Phase 3 | 予約と経路設計（IT4-7） | 60 | 23 | 進行中（IT4・IT5 完了。うち TS08/TS09/TS10 の 7 SP は Phase 3 外の新規ストーリー） |
| Phase 4 | 荷役と追跡（IT8-9） | 24 | 0 | 未着手 |
| Phase 5 | 精算（IT10） | 13 | 0 | 未着手 |
| **合計** | | **145** | **47** | |

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|
| v0.1.0 基盤と公開追跡 | - | 未着手 |
| v0.2.0 予約と経路設計 | - | 未着手 |
| v1.0.0 全業務フロー | - | 未着手 |

## GitHub 連携

| 項目 | リンク |
|------|--------|
| Project | [CargoTracker flix/take-1](https://github.com/users/k2works/projects/39) |
| マイルストーン | `[flix/take-1] Release 0.1.0 / 0.2.0 / 1.0.0` |
| Issue 命名規則 | `[flix/take-1][USxx or TSxx] タイトル` |
| ラベル | `flix/take-1`・`itN`・`sp:N`・`priority:*`・`user-story` / `type:technical-improvement` |

Issue は各イテレーションの開始準備（`opening-iteration`）時に、当該 IT 分のみ起票します。

## 補足

- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
