# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画・開発戦略

| ドキュメント | 説明 |
|-------------|------|
| [リリース計画](release_plan.md) | US01〜US27 を 4 フェーズ・7 イテレーション + 予備 1 で段階リリース（計 100 SP） |
| [開発戦略](development_strategy.md) | IT1-7 を序盤（アウトサイドイン）・中盤（インサイドアウト）・終盤（アウトサイドイン）の局面別 TDD で進める戦略 |

### イテレーション計画

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|-----------|-----------|------|
| IT1 | [計画](iteration_plan-1.md) | [ふりかえり](retrospective-1.md) | [完了報告書](iteration_report-1.md) | 完了 |
| IT2 | [計画](iteration_plan-2.md) | [ふりかえり](retrospective-2.md) | [完了報告書](iteration_report-2.md) | 完了 |
| IT3 | [計画](iteration_plan-3.md) | [ふりかえり](retrospective-3.md) | [完了報告書](iteration_report-3.md) | 完了 |
| IT4 | [計画](iteration_plan-4.md) | [ふりかえり](retrospective-4.md) | [完了報告書](iteration_report-4.md) | 完了 |
| IT5 | [計画](iteration_plan-5.md) | [ふりかえり](retrospective-5.md) | [完了報告書](iteration_report-5.md) | 完了 |
| IT6 | [計画](iteration_plan-6.md) | [ふりかえり](retrospective-6.md) | [完了報告書](iteration_report-6.md) | 完了 |
| IT7 | [計画](iteration_plan-7.md) | [ふりかえり](retrospective-7.md) | [完了報告書](iteration_report-7.md) | 完了 |
| IT8 | [計画](iteration_plan-8.md) | [ふりかえり](retrospective-8.md) | [完了報告書](iteration_report-8.md) | 完了（予備・受入基準の残充足） |

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 11 | 11 | 100% |
| IT2 | 13 | 13 | 100% |
| IT3 | 14 | 14 | 100% |
| IT4 | 15 | 15 | 100% |
| IT5 | 14 | 14 | 100% |
| IT6 | 15 | 15 | 100% |
| IT7 | 18 | 18 | 100% |
| **累計** | **100** | **100** | **100%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|
| Phase 1 | 基盤構築 + ユーザー認証 + 荷主・貨物予約登録 | 24 | 24 | 完了 |
| Phase 2 | 経路設計・予約確定・航海管理 | 29 | 29 | 完了 |
| Phase 3 | 追跡・荷役・例外処理 | 29 | 29 | 完了（IT5+IT6・US14-US20）※Release 0.3.0 発行済み |
| Phase 4 | 見積・料金計算・精算 | 18 | 18 | 完了（IT7・US01/US21/US22/US23・Estimation/Billing 新設・Release 1.0） |

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|

## クローズ前ローカル品質ゲート（CI 相当・T16）

イテレーションのクローズ前に、CI と同じ条件をローカルで再現して「ローカル緑・CI 赤」を防ぐ。過去 IT で RuboCop カスタム cop の autoload やシードデータ重複が CI だけで露見した教訓を手順化したもの。`apps/cargo-tracker` で以下を実行する。

```bash
# 1. seed 込みで test DB を再構築（CI の db:prepare 相当・シード起因の重複や FK 崩れを検出）
RAILS_ENV=test bundle exec rails db:drop db:prepare

# 2. eager load を明示検証（cop の LoadError・定数解決を本番同様に検出）
RAILS_ENV=test bundle exec rails runner 'Rails.application.eager_load!; puts "eager load ok"'

# 3. ランダム順で全 spec（順序依存・購読ポリューションを検出）
RAILS_ENV=test bundle exec rspec --order random

# 4. 静的解析（CI と同一ツールチェーン）
bundle exec rubocop --parallel
bundle exec brakeman -q
bundle exec bundler-audit check --update
bundle exec packwerk check
```

すべて緑であることを確認してから `closing-iteration` のステップ 2.5（`gh run` で CI 実結果確認）へ進む。

## 補足

- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
