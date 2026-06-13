'use strict';

import net from 'net';
import path from 'path';
import { execSync, spawn } from 'child_process';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

const PREFIX = 'DEV'; // 環境変数プレフィックス

/** サービス定義 */
const SERVICES = [
  { name: 'cargo-tracker', dir: 'apps/cargo-tracker', port: 9000, dbService: 'postgres', label: '国際貨物輸送管理システム' },
];

const APP = SERVICES[0];
const APP_DIR = path.join(process.cwd(), APP.dir);

// ============================================
// ヘルパー関数
// ============================================

/**
 * アプリケーションの起動ポートを取得する（SonarQube 等との競合時は DEV_APP_PORT で変更）
 * @returns {string} ポート番号
 */
function appPort() {
  return process.env[`${PREFIX}_APP_PORT`] || String(APP.port);
}

/**
 * 指定ポートが使用可能か確認する
 * @param {string|number} port - ポート番号
 * @returns {Promise<boolean>} 使用可能なら true
 */
function isPortAvailable(port) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.once('error', () => resolve(false));
    server.once('listening', () => server.close(() => resolve(true)));
    server.listen(Number(port), '0.0.0.0');
  });
}

/**
 * アプリの /health が応答するまで待機する
 * @param {string|number} port - ポート番号
 * @param {number} [timeoutMs=300000] - タイムアウト（ミリ秒）
 * @returns {Promise<boolean>} 応答したら true
 */
async function waitForHealth(port, timeoutMs = 300000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`http://localhost:${port}/health`, { signal: AbortSignal.timeout(5000) });
      if (res.ok) return true;
    } catch {
      // 起動待ち（接続不可・タイムアウトは無視してリトライ）
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  return false;
}

/**
 * Play 開発サーバーを起動する（終了までブロック）
 * @param {{ openBrowser?: boolean }} [options] - openBrowser: 起動完了後にブラウザでアプリを開く
 * @returns {Promise<void>}
 */
async function runAppServer(options = {}) {
  const port = appPort();
  if (!(await isPortAvailable(port))) {
    throw new Error(
      `ポート ${port} は使用中です（SonarQube 等が起動していないか確認してください）。` +
        `.env の ${PREFIX}_APP_PORT で別ポートを指定できます（例: ${PREFIX}_APP_PORT=9001）`
    );
  }
  console.log(`Starting Play dev server on http://localhost:${port} ...`);
  const child =
    process.platform === 'win32'
      ? spawn(`sbt "run ${port}"`, { cwd: APP_DIR, stdio: 'inherit', env: cleanDockerEnv(), shell: true })
      : spawn('sbt', [`run ${port}`], { cwd: APP_DIR, stdio: 'inherit', env: cleanDockerEnv() });
  if (options.openBrowser) {
    waitForHealth(port).then((ok) => {
      if (ok) {
        console.log(`Opening http://localhost:${port}/ ...`);
        openUrl(`http://localhost:${port}/`);
      }
    });
  }
  await new Promise((resolve, reject) => {
    child.on('error', reject);
    child.on('exit', (code) =>
      code === 0 || code === null ? resolve() : reject(new Error(`sbt run がコード ${code} で終了しました`))
    );
  });
}

/**
 * アプリディレクトリで docker compose コマンドを実行する
 * @param {string} args - docker compose に渡す引数
 */
function dockerCompose(args) {
  execSync(`docker compose ${args}`, { cwd: APP_DIR, stdio: 'inherit', env: cleanDockerEnv() });
}

/**
 * アプリディレクトリで sbt コマンドを実行する
 * @param {string} args - sbt に渡す引数
 */
function sbt(args) {
  execSync(`sbt ${args}`, { cwd: APP_DIR, stdio: 'inherit', env: cleanDockerEnv() });
}

/**
 * Docker が利用可能か確認し、不可なら警告メッセージを表示して false を返す
 * @returns {boolean} Docker が利用可能なら true
 */
function requireDocker() {
  if (isDockerAvailable()) {
    return true;
  }
  console.warn('Warning: Docker is not running. Skipping this task.');
  console.warn('Please start Docker Desktop and try again.');
  return false;
}

// ============================================
// Gulp タスク
// ============================================

/**
 * アプリケーション開発タスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 */
export default function (gulp) {
  // --- データベース ---

  gulp.task('dev:db:start', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log('Starting PostgreSQL...');
      dockerCompose(`up -d ${APP.dbService}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:db:stop', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      dockerCompose('down');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:db:logs', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      dockerCompose(`logs -f ${APP.dbService}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:db:psql', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      dockerCompose(`exec ${APP.dbService} psql -U cargo_tracker -d cargo_tracker`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // --- 開発サーバー ---

  gulp.task('dev:app', () => runAppServer());

  // 起動完了（/health 応答）後にブラウザでアプリを開く（npm run start 用）
  gulp.task('dev:app:open', () => runAppServer({ openBrowser: true }));

  // 開発サーバー起動（PostgreSQL 起動込み）
  gulp.task('dev', gulp.series('dev:db:start', 'dev:app'));

  // TDD モード（Testcontainers が PostgreSQL を自動起動するため compose 不要）
  gulp.task('tdd', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      sbt('~test');
      done();
    } catch (error) {
      done(error);
    }
  });

  // --- テスト・品質チェック ---

  gulp.task('dev:test', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      sbt('test');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:coverage', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      sbt('clean coverage test coverageReport');
      console.log(`\nレポート: ${APP.dir}/target/scala-3.3.*/scoverage-report/index.html`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:format', (done) => {
    try {
      sbt('scalafmtAll scalafixAll');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:check', (done) => {
    try {
      sbt('scalafmtCheckAll "scalafixAll --check"');
      done();
    } catch (error) {
      done(error);
    }
  });

  // --- ヘルプ ---

  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発コマンド（${APP.label}） ===

  dev                開発サーバー起動（PostgreSQL 起動込み・http://localhost:${appPort()}）
  dev:app            開発サーバーのみ起動（sbt run）
  dev:app:open       開発サーバー起動 + 起動完了後にブラウザを開く
  tdd                TDD モード（sbt ~test・ソース変更で自動再実行）

  dev:db:start       PostgreSQL を起動
  dev:db:stop        Docker サービスを停止
  dev:db:logs        PostgreSQL のログを表示
  dev:db:psql        PostgreSQL に接続（psql）

  dev:test           全テスト実行（sbt test）
  dev:coverage       カバレッジレポート生成（ゲート 80%）
  dev:format         フォーマット適用（scalafmt + scalafix）
  dev:check          品質チェック（CI と同一）

  dev:help           このヘルプを表示

環境変数（.env）:
  ${PREFIX}_APP_PORT       開発サーバーのポート（デフォルト ${APP.port}。SonarQube 等と競合する場合に変更）
`);
    done();
  });
}
