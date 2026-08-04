/**
 * 追跡 API の負荷試験（TS12b・IT8）。
 *
 * # なぜ自前で書くか
 *
 * k6 / Gatling を使う案も検討したが採らなかった。**追加の実行環境を要求すると、
 * 「手順はあるが誰も実行しない」状態になる**——IT7 のふりかえり Try T7 は
 * 「数値を持つ防御は、その数値が守られているか測ってから恒久化する」であり、
 * 測定が日常的に実行できることが前提である。
 *
 * Node.js は既にこのリポジトリの前提（gulp・arch-lint）であり、
 * `fetch` は Node 18 以降の標準である。**新しい依存を 1 つも足さない**。
 *
 * # 測るもの
 *
 * [非機能要件](../../../docs/design/non_functional.md) の目標値。
 *
 * | 対象 | 目標 |
 * | :--- | :--- |
 * | 平常時 | 50 RPS |
 * | ピーク | 200 RPS |
 * | 追跡 API | 1,000 RPS |
 *
 * # 測り方の注意
 *
 * **RPS を指定して投げるのではなく、同時実行数を指定して測る**。
 * 目標 RPS を投げつける形にすると、サーバが遅いときに送信側のキューが
 * 積み上がり、**測っているのが自分のキューなのかサーバなのか分からなくなる**。
 * 同時実行 N で回し、得られた RPS と応答時間を記録する。
 *
 * ランタイム防御（`RuntimeGuard`）が 503 を返した数も数える。
 * **503 は失敗ではない**——過負荷時に落とさず断ることが設計意図である
 * （IT7・TS12）。区別せずに「エラー率」へ混ぜると、防御が働いたことと
 * 壊れたことが同じ数字になる。
 */

const DEFAULT_BASE = 'http://localhost:8080';

/** 追跡番号（開発用シードが投入するもの） */
const DEFAULT_TRACKING = 'TRK-20260803-0001';

/**
 * 1 本のワーカー。終了時刻まで送り続ける。
 * @param {string} url 対象 URL
 * @param {number} endAt 終了時刻（ミリ秒）
 * @param {object} acc 集計オブジェクト
 */
async function worker(url, endAt, acc) {
  while (Date.now() < endAt) {
    const startedAt = Date.now();
    try {
      const res = await fetch(url, { redirect: 'manual' });
      const elapsed = Date.now() - startedAt;
      acc.latencies.push(elapsed);
      if (res.status === 503) acc.rejected += 1;
      else if (res.status === 504) acc.timedOut += 1;
      else if (res.status >= 500) acc.failed += 1;
      else acc.ok += 1;
      // **本文を読み切る**。読まないとコネクションが再利用されず、
      // 測っているのが接続の確立コストになる
      await res.text();
    } catch (e) {
      acc.errors += 1;
      acc.lastError = e.message;
    }
  }
}

/** 分位点（線形補間なし。件数が多いので単純な位置で十分） */
function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const index = Math.min(sorted.length - 1, Math.floor((sorted.length * p) / 100));
  return sorted[index];
}

/**
 * 1 条件を測る。
 * @param {string} url 対象 URL
 * @param {number} concurrency 同時実行数
 * @param {number} seconds 測定時間（秒）
 * @returns {Promise<object>} 測定結果
 */
async function measure(url, concurrency, seconds) {
  const acc = { ok: 0, rejected: 0, timedOut: 0, failed: 0, errors: 0, latencies: [] };
  const endAt = Date.now() + seconds * 1000;
  const startedAt = Date.now();
  await Promise.all(
    Array.from({ length: concurrency }, () => worker(url, endAt, acc)),
  );
  const elapsedSeconds = (Date.now() - startedAt) / 1000;
  const total = acc.ok + acc.rejected + acc.timedOut + acc.failed;
  const sorted = acc.latencies.slice().sort((a, b) => a - b);
  return {
    concurrency,
    seconds: Number(elapsedSeconds.toFixed(1)),
    total,
    rps: Number((total / elapsedSeconds).toFixed(1)),
    ok: acc.ok,
    rejected503: acc.rejected,
    timedOut504: acc.timedOut,
    failed5xx: acc.failed,
    errors: acc.errors,
    p50: percentile(sorted, 50),
    p95: percentile(sorted, 95),
    p99: percentile(sorted, 99),
    max: sorted.length > 0 ? sorted[sorted.length - 1] : 0,
    lastError: acc.lastError,
  };
}

/** 結果を 1 行で出す */
function report(label, r) {
  console.log(
    `${label.padEnd(22)} 同時${String(r.concurrency).padStart(4)} ` +
    `RPS ${String(r.rps).padStart(7)} ` +
    `2xx/3xx ${String(r.ok).padStart(6)} ` +
    `503 ${String(r.rejected503).padStart(5)} ` +
    `504 ${String(r.timedOut504).padStart(4)} ` +
    `5xx ${String(r.failed5xx).padStart(4)} ` +
    `err ${String(r.errors).padStart(4)} ` +
    `p50 ${String(r.p50).padStart(4)}ms p95 ${String(r.p95).padStart(5)}ms ` +
    `p99 ${String(r.p99).padStart(5)}ms`,
  );
}

async function main() {
  const base = process.env.LOAD_TEST_BASE || DEFAULT_BASE;
  const tracking = process.env.LOAD_TEST_TRACKING || DEFAULT_TRACKING;
  const seconds = Number(process.env.LOAD_TEST_SECONDS || 10);
  const url = `${base}/public/tracking/${tracking}`;

  console.log(`対象: ${url}`);
  console.log(`測定時間: 各 ${seconds} 秒\n`);

  // **ウォームアップを必ず入れる**。JIT とコネクションプールの初期化が
  // 1 回目の測定に乗ると、同じ条件でも 2 回目のほうが速く見える
  console.log('ウォームアップ中...');
  await measure(url, 10, 3);
  console.log('');

  const results = [];
  for (const concurrency of [10, 50, 100, 200, 400]) {
    const r = await measure(url, concurrency, seconds);
    report(`同時実行 ${concurrency}`, r);
    results.push(r);
  }

  console.log('\n--- 判定 ---');
  const best = results.reduce((a, b) => (b.rps > a.rps ? b : a));
  console.log(`最大 RPS: ${best.rps}（同時実行 ${best.concurrency}）`);
  const anyFailure = results.some((r) => r.failed5xx > 0 || r.errors > 0);
  console.log(anyFailure
    ? '**5xx またはエラーが発生した**。503（過負荷時の拒否）とは区別すること'
    : '5xx とエラーは 0 件（503 は設計どおりの拒否であり失敗ではない）');
  return results;
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
