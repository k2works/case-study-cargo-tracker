'use strict';

import path from 'path';
import { execSync, spawn } from 'child_process';
import { cleanDockerEnv } from './shared.js';

// ============================================
// 設定
// ============================================

/** アプリケーションのルートディレクトリ */
const APP_DIR = path.resolve('apps/cargo-tracker');

/** Web プロジェクトのパス（APP_DIR 相対） */
const WEB_PROJECT = 'src/CargoTracker.Web';

/** 単体テストプロジェクトのパス（APP_DIR 相対） */
const TEST_PROJECT = 'tests/CargoTracker.Tests';

/** アプリケーションのポート */
const APP_PORT = 8080;

/** PostgreSQL コンテナのサービス名（apps/cargo-tracker/docker-compose.yml） */
const DB_SERVICE = 'postgres';

// ============================================
// ヘルパー関数
// ============================================

/**
 * apps/cargo-tracker を作業ディレクトリとしてコマンドを同期実行する
 * @param {string} command - 実行するコマンド
 */
function run(command) {
  try {
    execSync(command, { cwd: APP_DIR, stdio: 'inherit', env: cleanDockerEnv() });
  } catch (err) {
    console.error(`エラー: ${command} が失敗しました (exit code: ${err.status})`);
    process.exit(1);
  }
}

/**
 * apps/cargo-tracker を作業ディレクトリとしてコマンドを前面プロセスで起動する
 * （dotnet run / dotnet watch などの常駐プロセス用。Ctrl+C で終了）
 * @param {string} command - コマンド名
 * @param {string[]} args - コマンド引数
 * @returns {import('child_process').ChildProcess}
 */
function runForeground(command, args) {
  return spawn(command, args, {
    cwd: APP_DIR,
    stdio: 'inherit',
    env: cleanDockerEnv(),
  });
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  // ------------------------------------------
  // 開発サーバー
  // ------------------------------------------

  gulp.task('dev:app', () => {
    console.log(`開発サーバーを起動します: http://localhost:${APP_PORT}`);
    return runForeground('dotnet', ['run', '--project', WEB_PROJECT]);
  });

  gulp.task('dev:app:watch', () => {
    console.log(`ホットリロード付きで起動します: http://localhost:${APP_PORT}`);
    return runForeground('dotnet', ['watch', 'run', '--project', WEB_PROJECT]);
  });

  // ------------------------------------------
  // TDD・テスト
  // ------------------------------------------

  gulp.task('tdd:backend', () => {
    console.log('TDD モード: 単体テストを自動再実行します（Ctrl+C で終了）');
    return runForeground('dotnet', ['watch', 'test', '--project', TEST_PROJECT]);
  });

  gulp.task('dev:test', (done) => {
    run('dotnet test');
    done();
  });

  gulp.task('dev:test:coverage', (done) => {
    run('dotnet test --collect:"XPlat Code Coverage"');
    run(
      'dotnet tool run reportgenerator -reports:"**/coverage.cobertura.xml" -targetdir:"coverage-report"'
    );
    console.log(`カバレッジレポート: ${path.join(APP_DIR, 'coverage-report/index.html')}`);
    done();
  });

  // カバレッジ CI ゲート（IT1 Try#1・ドメイン 85% / 全体 80%）。
  gulp.task('dev:test:coverage:gate', (done) => {
    run('dotnet test --collect:"XPlat Code Coverage"');
    run('node ops/scripts/coverage-gate.cjs');
    done();
  });

  // ------------------------------------------
  // 品質チェック
  // ------------------------------------------

  gulp.task('dev:format', (done) => {
    run('dotnet fantomas .');
    done();
  });

  gulp.task('dev:format:check', (done) => {
    run('dotnet fantomas --check .');
    done();
  });

  gulp.task('dev:lint', (done) => {
    run('dotnet fsharplint lint CargoTracker.sln');
    done();
  });

  gulp.task('dev:check', gulp.series('dev:format:check', 'dev:lint', 'dev:test'));

  // ------------------------------------------
  // データベース（PostgreSQL 本番互換テスト用）
  // ------------------------------------------

  gulp.task('dev:db:start', (done) => {
    run(`docker compose up -d ${DB_SERVICE}`);
    done();
  });

  gulp.task('dev:db:stop', (done) => {
    run('docker compose down');
    done();
  });

  gulp.task('dev:db:logs', (done) => {
    run(`docker compose logs --tail=100 ${DB_SERVICE}`);
    done();
  });

  gulp.task('dev:db:psql', () => {
    return runForeground('docker', [
      'compose',
      'exec',
      DB_SERVICE,
      'psql',
      '-U',
      'cargo_tracker',
      '-d',
      'cargo_tracker',
    ]);
  });

  // ------------------------------------------
  // ヘルプ
  // ------------------------------------------

  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発コマンド (apps/cargo-tracker) ===

  開発サーバー
    dev:app                開発サーバー起動（http://localhost:${APP_PORT}）
    dev:app:watch          ホットリロード付きで起動

  TDD・テスト
    tdd:backend            TDD モード（単体テストを自動再実行）
    dev:test               全テスト実行
    dev:test:coverage      テスト + カバレッジレポート生成

  品質チェック
    dev:format             Fantomas でフォーマット（自動修正）
    dev:format:check       フォーマットチェックのみ（CI 同等）
    dev:lint               FSharpLint（ソリューション単位・約 47 秒）
    dev:check              format:check → lint → test を一括実行

  データベース（本番互換テスト用 PostgreSQL 16 / ホストポート 5433）
    dev:db:start           PostgreSQL コンテナ起動
    dev:db:stop            コンテナ停止・削除
    dev:db:logs            ログ表示（直近 100 行）
    dev:db:psql            psql で接続

  dev:help                 このヘルプを表示
`);
    done();
  });
}
