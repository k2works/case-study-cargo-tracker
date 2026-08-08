# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画

| ドキュメント | 説明 |
|-------------|------|
| [リリーススコープ定義](release_scope.md) | **スコープの正典**。Release 1〜3 の US 配分、依存順序、スコープ外、リリース別の非機能目標・運用要件 |
| [リリース計画](release_plan.md) | ストーリーポイント・イテレーション配分・ベロシティ・リスク（30 US / 97SP） |
| [開発戦略](development_strategy.md) | 局面（序盤 / 中盤 / 終盤）別の TDD アプローチ・品質ゲートの段階的有効化・横断方針 |

### イテレーション計画

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|-----------|-----------|------|
| IT1 | [計画](iteration_plan-1.md) | [ふりかえり](retrospective-1.md) | [報告書](iteration_report-1.md) | **完了**（2026-08-06） |
| IT2 | [計画](iteration_plan-2.md) | [ふりかえり](retrospective-2.md) | [報告書](iteration_report-2.md) | **完了**（2026-08-06） |
| IT3 | [計画](iteration_plan-3.md) | [ふりかえり](retrospective-3.md) | [報告書](iteration_report-3.md) | **完了**（2026-08-07） |
| IT4 | [計画](iteration_plan-4.md) | [ふりかえり](retrospective-4.md) | [報告書](iteration_report-4.md) | **完了**（2026-08-07） |
| IT5 | [計画](iteration_plan-5.md) | [ふりかえり](retrospective-5.md) | [報告書](iteration_report-5.md) | **完了**（2026-08-07） |
| IT6 | [計画](iteration_plan-6.md) | [ふりかえり](retrospective-6.md) | [報告書](iteration_report-6.md) | **完了**（2026-08-08） |
| IT7 | [計画](iteration_plan-7.md) | [ふりかえり](retrospective-7.md) | [報告書](iteration_report-7.md) | **完了**（2026-08-08） |
| IT8 | [計画](iteration_plan-8.md) | [ふりかえり](retrospective-8.md) | [報告書](iteration_report-8.md) | **完了**（2026-08-08） |
| IT9 | [計画](iteration_plan-9.md) | [ふりかえり](retrospective-9.md) | [報告書](iteration_report-9.md) | **完了**（2026-08-09） |
| IT10 | [計画](iteration_plan-10.md) | — | — | **計画済み** |

イテレーション開始時に行を追加します。

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 9 | 9 | 100% |
| IT2 | 7 | 7 | 100% |
| IT3 | 8 | 8 | 100% |
| IT4 | 8 | 8 | 100% |
| IT5 | 7 | 7 | 100% |
| IT6 | 8 | 8 | 100% |
| **累計（完了分）** | **47** | **47** | **100%** |

ベロシティ初期値 12SP は**過大でした**。IT1〜IT6 の実績平均 7.83SP に基づき、**採用値を 8SP としています**（`release_plan.md`）。

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|
| Release 0.1 | 予約基盤（認証・荷主・予約） | 16 | 16 | **完了**（IT1 / IT2 とも完了） |
| Release 0.2 | 経路設計・予約確定 | 23 | 23 | **完了**（IT3・IT4・IT5） |
| Release 1.0 | 追跡（予約から追跡までの一気通貫） | 8 | 8 | **完了**（IT6） |
| Release 1.1 | 実運用に必要な補完 | 49 | 24 | **進行中**（IT7・IT8・IT9 完了。IT10〜IT12 が残り） |
| Release 2.0 | 精算 | 11 | 0 | 対象外（本計画の範囲外） |

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|

### GitHub 同期

| 項目 | 内容 |
|------|------|
| リポジトリ | [k2works/case-study-cargo-tracker](https://github.com/k2works/case-study-cargo-tracker) |
| Issue | 26 件（[`java/take-6`](https://github.com/k2works/case-study-cargo-tracker/issues?q=label%3Ajava%2Ftake-6) ラベル） |
| Milestone | 4 件（Release 0.1 / 0.2 / 1.0 / 1.1） |
| 最終同期 | 2026-08-08（IT6 クローズ時） |

Issue のタイトルは `[java/take-6][USxx] タイトル` 形式です。同一リポジトリを言語別 take が相乗りするため、ブランチ名をプレフィックスにしています。

## 補足

- 計画の正典は [リリース計画](release_plan.md)、スコープの正典は [リリーススコープ定義](release_scope.md) です。
- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
