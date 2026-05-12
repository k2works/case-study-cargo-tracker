'use strict';

import { spawn, spawnSync } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');
const composeFile = path.join(repoRoot, 'apps', 'docker-compose.yml');
const backendDir = path.join(repoRoot, 'apps', 'backend');

const isWindows = process.platform === 'win32';
const gradlewCmd = isWindows ? 'gradlew.bat' : './gradlew';

/**
 * 子プロセスを継承 stdio で起動し、終了コードを Promise で返す。
 * gulp タスクの done コールバックに渡せる Error または null を返す。
 */
function runStreaming(cmd, args, opts = {}) {
  return new Promise((resolve) => {
    console.log(`> ${cmd} ${args.join(' ')}${opts.cwd ? ` (cwd=${opts.cwd})` : ''}`);
    const child = spawn(cmd, args, {
      stdio: 'inherit',
      shell: isWindows,
      ...opts,
    });
    child.on('close', (code) => {
      if (code === 0) {
        resolve(null);
      } else {
        resolve(new Error(`${cmd} exited with code ${code}`));
      }
    });
  });
}

/**
 * 開発支援タスクを gulp に登録する。
 *
 * 提供タスク:
 *   - dev:bookingms : bookingms を IDE 相当の bootRun で起動（local-h2）
 *   - tdd:bookingms : bookingms のテストを continuous モードで実行
 *   - up:basics     : Axon Server + PostgreSQL のみ起動（日常 TDD ループ）
 *   - up:all        : Phase 0 で実装済みの全サービスを起動
 *                     （現状は基盤 + bookingms = `booking` プロファイル相当）
 *                     Phase 1 で他サービスが実装されたら `full` プロファイルに切り替える
 *   - down:all      : compose down（ボリュームは保持）
 *   - down:clean    : compose down -v（ボリューム完全削除、要明示）
 *   - smoke         : 主要エンドポイントの疎通確認
 *
 * @param {import('gulp').Gulp} gulp
 */
export default function (gulp) {
  gulp.task('dev:bookingms', async (done) => {
    const err = await runStreaming(
      gradlewCmd,
      [':bookingms:bootRun', '--args=--spring.profiles.active=local-h2'],
      { cwd: backendDir }
    );
    done(err);
  });

  gulp.task('tdd:bookingms', async (done) => {
    const err = await runStreaming(
      gradlewCmd,
      [':bookingms:test', '--continuous'],
      { cwd: backendDir }
    );
    done(err);
  });

  gulp.task('up:basics', async (done) => {
    const err = await runStreaming('docker', [
      'compose', '-f', composeFile,
      'up', '-d', 'axonserver', 'postgresql',
    ]);
    done(err);
  });

  gulp.task('up:all', async (done) => {
    // Phase 0: docker-compose.yml で実体定義されているのは bookingms のみ（他は雛形・コメントアウト）。
    // profile 指定なしで全アクティブサービスを起動し、対の down:all とも対称になる。
    // Phase 1 で他サービスが復活したら `--profile full` を再導入する。
    const err = await runStreaming('docker', [
      'compose', '-f', composeFile, 'up', '-d',
    ]);
    done(err);
  });

  gulp.task('down:all', async (done) => {
    const err = await runStreaming('docker', [
      'compose', '-f', composeFile, 'down',
    ]);
    done(err);
  });

  gulp.task('down:clean', async (done) => {
    if (process.env.CONFIRM_CLEAN !== 'yes') {
      console.error('down:clean はボリュームを破棄します。実行するには CONFIRM_CLEAN=yes を設定してください。');
      done(new Error('Confirmation required'));
      return;
    }
    const err = await runStreaming('docker', [
      'compose', '-f', composeFile, 'down', '-v',
    ]);
    done(err);
  });

  gulp.task('smoke', (done) => {
    const targets = [
      { name: 'Axon Server', url: `http://localhost:${process.env.AXON_SERVER_HTTP_PORT || 8024}/actuator/health` },
      { name: 'bookingms',   url: 'http://localhost:8082/actuator/health' },
    ];
    let failed = 0;
    for (const t of targets) {
      const result = spawnSync(
        isWindows ? 'curl.exe' : 'curl',
        ['-fsS', '--max-time', '5', t.url],
        { stdio: 'inherit' }
      );
      if (result.status === 0) {
        console.log(`OK : ${t.name} (${t.url})`);
      } else {
        console.error(`NG : ${t.name} (${t.url})`);
        failed += 1;
      }
    }
    done(failed === 0 ? null : new Error(`${failed} smoke check(s) failed`));
  });

  gulp.task('dev:help', (done) => {
    console.log(`
開発支援タスク一覧
==================

  dev:bookingms   bookingms を local-h2 プロファイルで bootRun
  tdd:bookingms   bookingms の Gradle test --continuous

  up:basics       Axon Server + PostgreSQL のみ起動
  up:all          Phase 0 で起動可能な全サービス（基盤 + bookingms）を起動
                  ※ Phase 1 で残り 6 サービス + Frontend を docker-compose.yml にアンコメント
  down:all        compose down（ボリュームは保持）
  down:clean      compose down -v（要 CONFIRM_CLEAN=yes、Event Store を破棄）

  smoke           Axon Server / bookingms の health 疎通確認

注意:
  - up:basics / up:all は .env で POSTGRES_PASSWORD などのシークレット必須
  - down:clean は Event Store も削除する破壊的操作（ADR-0003 参照）
`);
    done();
  });
}
