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

## CI 統合 (T6-06 予定)

`.github/workflows/ci.yml` に main branch push 時のスモークテストジョブを追加:

```yaml
smoke-load-test:
  needs: [deploy-staging]
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: grafana/setup-k6-action@v1
    - name: k6 smoke test
      env:
        BASE_URL: ${{ secrets.STAGING_BASE_URL }}
        TRACKING_NUMBER: ${{ secrets.SMOKE_TRACKING_NUMBER }}
      run: |
        k6 run --summary-export=k6-summary.json \
          apps/cargo-tracker/scripts/k6/smoke-tracking.js

        # P95 抽出と SLA チェック
        p95=$(jq '.metrics.http_req_duration.values["p(95)"]' k6-summary.json)
        awk -v p="$p95" 'BEGIN { exit (p >= 500) }' \
          || (echo "P95 SLA 違反: ${p95}ms" && exit 1)

    - name: Upload summary
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: k6-summary
        path: k6-summary.json
```

## 判定ルール

- **合格**: 全 3 SLA (P95 / P99 / エラー率) をクリア
- **不合格**: いずれか 1 つでも違反 → CI ジョブが exit 1 で失敗、デプロイブロック
- **警告** (将来): 直近 7 回の実行結果と比較して P95 が +30% 悪化した場合、
  Slack 通知のみ (ブロックなし、リグレッション早期検知)

## 参照

- `docs/design/test_strategy.md §9. パフォーマンステスト (Release 1.0 から CI 組み込み)`
- iteration_plan-7.md §7.4 k6 smoke script + CI job (T6-06)
