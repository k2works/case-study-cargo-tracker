/**
 * 負荷試験の gulp タスク（TS12b・IT8）。
 *
 * **アプリは別途起動しておく**。タスクの中で起動しないのは、
 * 測定対象の構成（環境変数・DB）を測る側が決めてしまわないためである。
 * 本番に近い構成で測りたいときは `DATABASE_URL` を与えて起動する。
 */

import { spawn } from 'node:child_process';

export default function (gulp) {
  gulp.task('load:test', (done) => {
    const child = spawn('node', ['ops/scripts/load-test/index.js'], {
      stdio: 'inherit',
      env: process.env,
    });
    child.on('exit', (code) => {
      if (code !== 0) {
        done(new Error(`負荷試験が失敗しました（終了コード ${code}）`));
        return;
      }
      done();
    });
  });

  gulp.task('load:help', (done) => {
    console.log(`
=== 負荷試験（追跡 API） ===

  load:test        追跡 API に負荷をかけて RPS と応答時間を測る

前提:
  1. アプリを起動しておく（例: cd apps/cargo-tracker && java -jar ../../ops/tools/flix/flix.jar run）
  2. 開発用シードの追跡番号（TRK-20260803-0001）が入っていること

環境変数:
  LOAD_TEST_BASE       対象のベース URL（既定 http://localhost:8080）
  LOAD_TEST_TRACKING   追跡番号（既定 TRK-20260803-0001）
  LOAD_TEST_SECONDS    各条件の測定時間（既定 10 秒）

結果の読み方は docs/design/non_functional.md「実測値」を参照。
**503 は失敗ではない**（過負荷時に落とさず断る設計。IT7・TS12）。
`);
    done();
  });
}
