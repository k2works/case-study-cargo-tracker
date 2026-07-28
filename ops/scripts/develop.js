'use strict';

/**
 * アプリケーション開発タスク。
 * apps/ 配下の各アプリの npm scripts を Gulp タスクとしてラップし、
 * 開発サーバー起動・テスト・TDD・E2E・ビルド・品質検証を統一コマンドで提供する。
 */

import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';
import { cleanDockerEnv } from './shared.js';

// ============================================
// 設定
// ============================================

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(__dirname, '..', '..');

/** 開発対象アプリ定義 */
const APPS = [
  {
    name: 'cargo-tracker',
    dir: path.join(ROOT_DIR, 'apps', 'cargo-tracker'),
    port: 8080,
    label: 'Cargo Tracker（国際貨物輸送管理システム）',
  },
];

// ============================================
// ヘルパー関数
// ============================================

/**
 * 指定アプリのディレクトリで npm スクリプトを実行する
 * @param {{ name: string, dir: string, label: string }} app - 対象アプリ定義
 * @param {string} script - 実行する npm スクリプト名（package.json の scripts）
 * @returns {void}
 */
function runNpm(app, script) {
  const cmd = `npm run ${script}`;
  try {
    execSync(cmd, { cwd: app.dir, stdio: 'inherit', env: cleanDockerEnv() });
  } catch (err) {
    console.error(`エラー: ${app.label} の "${script}" 実行に失敗しました: ${err.message}`);
    process.exit(1);
  }
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  APPS.forEach((app) => {
    // 開発サーバー（ライブリロード付き。node --watch + livereload）
    gulp.task(`dev:${app.name}`, (done) => {
      runNpm(app, 'dev');
      done();
    });

    // 開発サーバー（再起動なしの単発起動）
    gulp.task(`dev:${app.name}:serve`, (done) => {
      runNpm(app, 'serve');
      done();
    });

    // 単体・統合テスト（一回）
    gulp.task(`dev:${app.name}:test`, (done) => {
      runNpm(app, 'test');
      done();
    });

    // TDD モード（ウォッチ実行）
    gulp.task(`tdd:${app.name}`, (done) => {
      runNpm(app, 'test:watch');
      done();
    });

    // カバレッジ計測
    gulp.task(`dev:${app.name}:coverage`, (done) => {
      runNpm(app, 'test:coverage');
      done();
    });

    // E2E テスト（Playwright）
    gulp.task(`dev:${app.name}:e2e`, (done) => {
      runNpm(app, 'test:e2e');
      done();
    });

    // 本番ビルド
    gulp.task(`dev:${app.name}:build`, (done) => {
      runNpm(app, 'build');
      done();
    });

    // 品質検証（lint + typecheck + arch + test）
    gulp.task(`dev:${app.name}:verify`, (done) => {
      runNpm(app, 'verify');
      done();
    });

    // 静的解析・型検査・アーキテクチャ検証（テストなし）
    gulp.task(`dev:${app.name}:check`, (done) => {
      runNpm(app, 'check');
      done();
    });

    // コードフォーマット
    gulp.task(`dev:${app.name}:format`, (done) => {
      runNpm(app, 'format');
      done();
    });

    // DB マイグレーション（実 PostgreSQL。DATABASE_URL 必須）
    gulp.task(`dev:${app.name}:migrate`, (done) => {
      runNpm(app, 'migrate:up');
      done();
    });
  });

  // 既定アプリ（単一アプリ構成の短縮エイリアス）
  const primary = APPS[0];
  if (primary) {
    gulp.task('dev', gulp.series(`dev:${primary.name}`));
    gulp.task('tdd', gulp.series(`tdd:${primary.name}`));
    gulp.task('verify', gulp.series(`dev:${primary.name}:verify`));
  }

  // ヘルプタスク（必須）
  gulp.task('dev:help', (done) => {
    const lines = APPS.map(
      (app) => `
  ${app.label}（ポート ${app.port}）
    dev:${app.name}            開発サーバー起動（ライブリロード付き）
    dev:${app.name}:serve      開発サーバー起動（再起動なし）
    dev:${app.name}:test       単体・統合テスト
    tdd:${app.name}            TDD モード（ウォッチ）
    dev:${app.name}:coverage   カバレッジ計測
    dev:${app.name}:e2e        E2E テスト（Playwright）
    dev:${app.name}:build      本番ビルド
    dev:${app.name}:verify     品質検証（lint+typecheck+arch+test）
    dev:${app.name}:check      静的解析・型検査・アーキテクチャ検証
    dev:${app.name}:format     コードフォーマット
    dev:${app.name}:migrate    DB マイグレーション（DATABASE_URL 必須）`,
    ).join('\n');
    console.log(`
=== アプリケーション開発コマンド ===
${lines}

  短縮エイリアス（既定アプリ ${primary ? primary.name : '-'}）:
    dev       = dev:${primary ? primary.name : ''}
    tdd       = tdd:${primary ? primary.name : ''}
    verify    = dev:${primary ? primary.name : ''}:verify

  dev:help                  このヘルプを表示
`);
    done();
  });
}
