# k6 スモーク負荷テスト (T6-06, IT7)

`docs/design/test_strategy.md §9. パフォーマンステスト` に基づく CI 統合スモーク
負荷テストのスクリプト集。

## スクリプト一覧

| スクリプト | 対象 | 想定 SLA |
| :--- | :--- | :--- |
| `smoke-tracking.js` | GET `/public/tracking/:trackingNumber` (US18) | P95 < 500ms / P99 < 1000ms / エラー率 < 0.1% |

## ローカル実行

```bash
# k6 v0.50.x 以上を推奨 (https://k6.io/docs/getting-started/installation/)
brew install k6  # macOS
# apt install k6 # Ubuntu (公式リポジトリ設定後)

# 対象アプリを起動してから実行
k6 run \
  --summary-export=k6-summary.json \
  apps/cargo-tracker/scripts/k6/smoke-tracking.js
```

環境変数:

- `BASE_URL`: 対象ホスト (デフォルト `http://localhost:8080`)
- `TRACKING_NUMBER`: リクエストする追跡番号 (デフォルト `TR000001`)

例 (ステージング環境):

```bash
BASE_URL=https://staging.cargo-tracker.example.com \
TRACKING_NUMBER=TR123ABC \
  k6 run apps/cargo-tracker/scripts/k6/smoke-tracking.js
```

## CI 統合 (`.github/workflows/k6-smoke.yml`)

現状の CI ワークフロー: **手動起動 (`workflow_dispatch`) のみ**。ステージング環境が
未構築のため、以下の手順で任意タイミング実行できる:

1. GitHub Actions タブから `k6 Smoke Load Test` ワークフローを選択
2. `Run workflow` ボタン →
   - `base_url`: 対象ホスト (例: `http://localhost:8080` を ngrok で公開、または将来のステージング URL)
   - `tracking_number`: リクエストする追跡番号 (デフォルト `TR000001`)

ワークフローの動作:

- k6 v0.50.0 をインストール → `smoke-tracking.js` 実行 (10 VUs × 60 秒)
- `k6-summary.json` を解析して SLA メトリクス (P95 / P99 / エラー率) を GitHub Summary に表示
- **P95 >= 500ms なら exit 1 でジョブ失敗**
- `k6-summary.json` を 30 日間の Artifact として保存

ステージング環境完成後は `push: branches: [main]` トリガーのコメントアウトを解除し、
デプロイ後の自動スモークに切り替える予定。

## 判定ルール

- **合格**: 全 3 SLA (P95 / P99 / エラー率) をクリア
- **不合格**: いずれか 1 つでも違反 → CI ジョブが exit 1 で失敗、デプロイブロック
- **警告** (将来): 直近 7 回の実行結果と比較して P95 が +30% 悪化した場合、
  Slack 通知のみ (ブロックなし、リグレッション早期検知)

## 参照

- `docs/design/test_strategy.md §9. パフォーマンステスト (Release 1.0 から CI 組み込み)`
- iteration_plan-7.md §7.4 k6 smoke script + CI job (T6-06)
