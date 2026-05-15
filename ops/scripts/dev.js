'use strict';

import { spawn, spawnSync } from 'child_process';
import path from 'path';
import readline from 'readline';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');
const composeFile = path.join(repoRoot, 'apps', 'docker-compose.yml');
const backendDir = path.join(repoRoot, 'apps', 'backend');

const isWindows = process.platform === 'win32';
const gradlewCmd = isWindows ? 'gradlew.bat' : './gradlew';

/**
 * 破壊的操作の前に y/n 確認を取る。
 *
 * - TTY 接続時は readline で対話的にプロンプトを出す（y を入力すると続行、それ以外は中断）。
 * - 非対話実行（CI 等で stdin が TTY でない）の場合は、誤実行防止のため自動的に false を返す。
 */
function confirmDestructive(message) {
  if (!process.stdin.isTTY) {
    console.error(
      `${message} 非対話環境では実行を中断します。対話端末から再実行してください。`,
    );
    return Promise.resolve(false);
  }
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    rl.question(`${message} [y/N]: `, (answer) => {
      rl.close();
      resolve(answer.trim().toLowerCase() === 'y');
    });
  });
}

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

  // ==========================================================================
  // local-docker プロファイル運用タスク
  //
  // docker-compose.yml と application-local-docker.yml で構成する
  // フルスタック（Axon Server + PostgreSQL + authms + bookingms + routingms +
  // gatewayms）の構築・起動・停止・リセット・疎通確認を提供する。
  // ==========================================================================

  gulp.task('local-docker:build', async (done) => {
    // 全サービスの Docker イメージをビルド（変更があるサービスのみ再ビルドされる）。
    // `local-docker:up` は build を含むが、起動を伴わずビルドだけ実行したい場合に使う。
    const err = await runStreaming('docker', [
      'compose', '-f', composeFile, 'build',
    ]);
    done(err);
  });

  gulp.task('local-docker:up', async (done) => {
    // 基盤 + authms + bookingms + routingms + gatewayms を起動（IT2 時点の全構成）。
    // frontend は IT3 で追加予定。docker-compose.yml の services セクションを参照。
    const err = await runStreaming('docker', [
      'compose', '-f', composeFile, 'up', '-d',
    ]);
    done(err);
  });

  gulp.task('local-docker:down', async (done) => {
    // コンテナとネットワークを停止・削除（ボリュームは保持＝Event Store / DB は残る）。
    const err = await runStreaming('docker', [
      'compose', '-f', composeFile, 'down',
    ]);
    done(err);
  });

  gulp.task('local-docker:clean', async (done) => {
    // ボリュームを含めた完全リセット（Axon Server Event Store・PostgreSQL データを破棄）。
    // 誤実行を防ぐため、対話的に y/n 確認を取る。
    const ok = await confirmDestructive(
      'local-docker:clean は Axon Server Event Store と PostgreSQL データを破棄します。続行しますか？',
    );
    if (!ok) {
      console.log('local-docker:clean をキャンセルしました。');
      done();
      return;
    }
    const err = await runStreaming('docker', [
      'compose', '-f', composeFile, 'down', '-v',
    ]);
    done(err);
  });

  gulp.task('local-docker:smoke', (done) => {
    // Axon Server + authms + bookingms + routingms + gatewayms の health 疎通を確認。
    const targets = [
      { name: 'Axon Server', url: `http://localhost:${process.env.AXON_SERVER_HTTP_PORT || 8024}/actuator/health` },
      { name: 'authms',      url: 'http://localhost:8081/actuator/health' },
      { name: 'bookingms',   url: 'http://localhost:8082/actuator/health' },
      { name: 'routingms',   url: 'http://localhost:8083/actuator/health' },
      { name: 'gatewayms',   url: 'http://localhost:8080/actuator/health' },
    ];
    runSmoke(targets, done);
  });

  function runSmoke(targets, done) {
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
  }

  gulp.task('dev:help', (done) => {
    console.log(`
開発支援タスク一覧
==================

  local-h2 プロファイル（IDE 個別 bootRun・最速 TDD ループ）
  -----------------------------------------------------------
  dev:bookingms        bookingms を local-h2 プロファイルで bootRun
  tdd:bookingms        bookingms の Gradle test --continuous

  local-docker プロファイル（Axon Server + PostgreSQL + 全 4 ms をフル起動）
  ------------------------------------------------------------------------
  local-docker:build   全サービスの Docker イメージをビルド
  local-docker:up      全サービス起動（基盤 + authms + bookingms + routingms + gatewayms）
  local-docker:down    コンテナ停止（ボリュームは保持）
  local-docker:clean   完全リセット（実行前に y/n 確認、Event Store・DB を破棄）
  local-docker:smoke   Axon Server + 4 ms の health 疎通確認

注意:
  - local-docker:* は .env で POSTGRES_PASSWORD・JWT_SECRET 等のシークレット必須
  - local-docker:clean は Event Store も削除する破壊的操作。実行前に y/n 確認あり（ADR-0003 参照）
  - local-h2 では SubscribingEventProcessor、local-docker では Axon Server 接続前提
    （ADR-0008 参照）
`);
    done();
  });
}
