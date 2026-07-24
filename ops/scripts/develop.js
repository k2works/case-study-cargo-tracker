'use strict';

import { execSync, spawn } from 'child_process';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

/** cargo-tracker アプリケーションのルートディレクトリ */
const APP_DIR = 'apps/cargo-tracker';

/** サービス定義 */
const SERVICES = [
  { name: 'backend', port: 8080, label: 'cargo-tracker（バックエンド）' },
];

// ============================================
// ヘルパー関数
// ============================================

/**
 * make タスクを同期実行する
 * @param {string} target - Makefile のターゲット名
 * @param {import('child_process').ExecSyncOptions} [options] - execSync オプション
 */
function make(target, options = {}) {
  execSync(`make ${target}`, {
    cwd: APP_DIR,
    stdio: 'inherit',
    env: cleanDockerEnv(),
    ...options,
  });
}

/**
 * make タスクを非同期（子プロセス）で実行する。
 * サーバー起動や継続テストなど、常駐するタスクに使用する。
 * @param {string} target - Makefile のターゲット名
 * @param {import('gulp').TaskFunctionCallback} done - Gulp 完了コールバック
 */
function makeSpawn(target, done) {
  const child = spawn('make', [target], {
    cwd: APP_DIR,
    stdio: 'inherit',
    shell: true,
    env: cleanDockerEnv(),
  });
  child.on('close', (code) => done(code ? new Error(`Exit code: ${code}`) : undefined));
}

/**
 * docker compose コマンドを実行する
 * @param {string} subcommand - docker compose のサブコマンドと引数
 */
function compose(subcommand) {
  if (!isDockerAvailable()) {
    console.error('エラー: Docker デーモンに接続できません。Docker を起動してください。');
    process.exit(1);
  }
  execSync(`docker compose ${subcommand}`, {
    cwd: APP_DIR,
    stdio: 'inherit',
    env: cleanDockerEnv(),
  });
}

// ============================================
// Gulp タスク
// ============================================

/**
 * cargo-tracker（Go）用アプリケーション開発タスクを登録する
 * @param {import('gulp')} gulp
 */
export default function developTasks(gulp) {
  // --- データベース（PostgreSQL / docker compose） ---

  gulp.task('dev:db:start', (done) => {
    console.log('PostgreSQL を起動します...');
    compose('up -d postgres');
    done();
  });

  gulp.task('dev:db:stop', (done) => {
    console.log('PostgreSQL を停止します...');
    compose('stop postgres');
    done();
  });

  gulp.task('dev:db:clean', (done) => {
    console.log('PostgreSQL を停止しボリュームを削除します...');
    compose('down -v');
    done();
  });

  gulp.task('dev:db:status', (done) => {
    compose('ps');
    done();
  });

  // --- アプリケーション ---

  gulp.task('dev:run', (done) => {
    console.log('開発サーバーを起動します（http://localhost:8080）...');
    makeSpawn('run', done);
  });

  // ライブリロード（air によるホットリロード）
  gulp.task('dev:watch', (done) => {
    console.log('開発サーバーをライブリロード起動します（http://localhost:8080）...');
    makeSpawn('watch', done);
  });

  gulp.task('dev:build', (done) => {
    try {
      make('build');
      done();
    } catch (e) {
      done(e);
    }
  });

  gulp.task('dev:open', (done) => {
    openUrl(`http://localhost:${SERVICES[0].port}`);
    done();
  });

  // --- テスト ---

  gulp.task('dev:test', (done) => {
    try {
      make('test');
      done();
    } catch (e) {
      done(e);
    }
  });

  gulp.task('dev:test:integration', (done) => {
    try {
      make('test-integration');
      done();
    } catch (e) {
      done(e);
    }
  });

  gulp.task('dev:test:all', (done) => {
    try {
      make('test-all');
      done();
    } catch (e) {
      done(e);
    }
  });

  // TDD モード（gow によるテスト自動再実行）
  gulp.task('tdd:backend', (done) => {
    console.log('TDD モードを起動します（テスト自動再実行）...');
    makeSpawn('tdd', done);
  });

  // --- 品質チェック ---

  gulp.task('dev:lint', (done) => {
    try {
      make('lint');
      done();
    } catch (e) {
      done(e);
    }
  });

  gulp.task('dev:arch', (done) => {
    try {
      make('arch');
      done();
    } catch (e) {
      done(e);
    }
  });

  gulp.task('dev:check', (done) => {
    try {
      make('check');
      done();
    } catch (e) {
      done(e);
    }
  });

  // --- コード生成・マイグレーション ---

  gulp.task('dev:generate', (done) => {
    try {
      make('generate');
      done();
    } catch (e) {
      done(e);
    }
  });

  gulp.task('dev:migrate:up', (done) => {
    try {
      make('migrate-up');
      done();
    } catch (e) {
      done(e);
    }
  });

  gulp.task('dev:migrate:down', (done) => {
    try {
      make('migrate-down');
      done();
    } catch (e) {
      done(e);
    }
  });

  // --- ヘルプ ---

  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発コマンド（cargo-tracker / Go） ===

  データベース:
    dev:db:start            PostgreSQL を起動
    dev:db:stop             PostgreSQL を停止
    dev:db:clean            PostgreSQL を停止しボリュームを削除
    dev:db:status           コンテナ状態を確認

  アプリケーション:
    dev:run                 開発サーバーを起動（http://localhost:8080）
    dev:watch               開発サーバーをライブリロード起動（要 gow）
    dev:build               ビルド
    dev:open                ブラウザで開く

  テスト:
    dev:test                単体テスト（カバレッジ付き）
    dev:test:integration    統合テスト（testcontainers-go。Docker 必須）
    dev:test:all            全テスト
    tdd:backend             TDD モード（テスト自動再実行。要 gow）

  品質チェック:
    dev:lint                静的解析（golangci-lint + govulncheck）
    dev:arch                アーキテクチャルール検証（go-arch-lint）
    dev:check               ビルド + テスト + Lint + アーキ検証

  コード生成・マイグレーション:
    dev:generate            sqlc コード生成
    dev:migrate:up          マイグレーション適用
    dev:migrate:down        マイグレーションを 1 ステップ戻す

    dev:help                このヘルプを表示
  `);
    done();
  });
}
