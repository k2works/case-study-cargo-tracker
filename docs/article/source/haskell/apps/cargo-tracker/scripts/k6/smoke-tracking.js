// k6 smoke load test for GET /public/tracking/:trackingNumber (US18, T6-06 / IT7)
//
// テスト戦略 (docs/design/test_strategy.md §9.1) に基づく非機能要件検証:
// - 対象: `/public/tracking/:number` (認証不要、最頻アクセス)
// - シナリオ: 10 rps × 60 秒 (600 リクエスト想定)
// - SLA: P95 < 500ms / P99 < 1000ms / エラー率 < 0.1%
//
// 実行方法 (ローカル):
//   k6 run --summary-export=k6-summary.json apps/cargo-tracker/scripts/k6/smoke-tracking.js
//
// 環境変数:
//   BASE_URL: 対象ホスト (デフォルト http://localhost:8080)
//   TRACKING_NUMBER: リクエストする追跡番号 (デフォルト TR000001)
//
// CI 統合 (T6-06):
//   - main branch push 時にステージング環境で実行 (E2E と同時)
//   - P95 < 500ms を超えたらデプロイブロック + Slack 通知
//   - 直近 7 回の実行結果を CloudWatch カスタムメトリクスに記録

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// カスタムメトリクス
const errorRate = new Rate('errors');
const trackingLatency = new Trend('tracking_latency', true);

export const options = {
  vus: 10,
  duration: '60s',
  thresholds: {
    // docs/design/test_strategy.md §9.1 の判定値と整合
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.001'],
    errors: ['rate<0.001'],
    checks: ['rate>0.999'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TRACKING_NUMBER = __ENV.TRACKING_NUMBER || 'TR000001';

export default function () {
  const url = `${BASE_URL}/public/tracking/${TRACKING_NUMBER}`;
  const res = http.get(url, {
    tags: { name: 'GET /public/tracking/:tn' },
  });

  trackingLatency.add(res.timings.duration);

  const ok = check(res, {
    'status 200 or 404': (r) => r.status === 200 || r.status === 404,
    'response time < 500ms': (r) => r.timings.duration < 500,
    'response has body': (r) => r.body && r.body.length > 0,
  });

  errorRate.add(!ok);

  // 各 VU の request rate を平均 1 rps 相当にする (10 VUs × 1 rps = 10 rps 目標)
  sleep(1);
}

// テスト結果サマリー (実行終了時に stdout + JSON に出力)
export function handleSummary(data) {
  const p95 = data.metrics.http_req_duration.values['p(95)'];
  const p99 = data.metrics.http_req_duration.values['p(99)'];
  const errRate = data.metrics.http_req_failed.values.rate;

  const summary =
    '\n=== k6 smoke test summary ===\n' +
    `P95 latency: ${p95.toFixed(1)}ms (SLA: < 500ms)\n` +
    `P99 latency: ${p99.toFixed(1)}ms (SLA: < 1000ms)\n` +
    `Error rate:  ${(errRate * 100).toFixed(3)}% (SLA: < 0.1%)\n`;

  return {
    stdout: summary,
    'k6-summary.json': JSON.stringify(data, null, 2),
  };
}
