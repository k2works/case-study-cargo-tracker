'use strict';

import path from 'path';
import { execSync } from 'child_process';
import { cleanDockerEnv } from './shared.js';

// ============================================
// 設定
// ============================================

/** アプリケーションのルートディレクトリ */
const APP_DIR = path.resolve('apps/cargo-tracker');

/** 開発サーバーのポート */
const APP_PORT = process.env.DEV_APP_PORT || '3000';

/** Docker Compose の PostgreSQL サービス名 */
const DB_SERVICE = 'postgres';

// ============================================
// ヘルパー関数
// ============================================

/**
 * rbenv shims を先頭に追加した環境変数を返す
 * Gulp 実行シェルの Ruby がシステム Ruby でも .ruby-version の Ruby を解決できるようにする
 * @returns {Object} PATH を調整した環境変数
 */
function rubyEnv() {
  const env = cleanDockerEnv();
  const shims = path.join(process.env.HOME || '', '.rbenv', 'shims');
  env.PATH = `${shims}${path.delimiter}${env.PATH}`;
  return env;
}

/**
 * アプリケーションディレクトリでコマンドを実行する
 * @param {string} command - 実行するコマンド
 */
function appExec(command) {
  try {
    execSync(command, { cwd: APP_DIR, stdio: 'inherit', env: rubyEnv() });
  } catch (err) {
    console.error(`エラー: ${command} が失敗しました`);
    process.exit(1);
  }
}

/**
 * プロジェクトルートで Docker Compose コマンドを実行する
 * @param {string} args - docker compose に渡す引数
 */
function composeExec(args) {
  try {
    execSync(`docker compose ${args}`, { stdio: 'inherit', env: cleanDockerEnv() });
  } catch (err) {
    console.error(`エラー: docker compose ${args} が失敗しました`);
    process.exit(1);
  }
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  // --- データベース ---

  gulp.task('dev:db:start', (done) => {
    composeExec(`up -d ${DB_SERVICE}`);
    done();
  });

  gulp.task('dev:db:stop', (done) => {
    composeExec(`stop ${DB_SERVICE}`);
    done();
  });

  gulp.task('dev:db:logs', (done) => {
    composeExec(`logs --tail=100 ${DB_SERVICE}`);
    done();
  });

  gulp.task('dev:db:connect', (done) => {
    composeExec(`exec ${DB_SERVICE} psql -U cargo_tracker -d cargo_tracker_development`);
    done();
  });

  gulp.task('dev:db:prepare', (done) => {
    appExec('bin/rails db:prepare');
    done();
  });

  gulp.task('dev:db:migrate', (done) => {
    appExec('bin/rails db:migrate');
    done();
  });

  // --- 開発サーバー ---

  gulp.task('dev:server', gulp.series('dev:db:start', (done) => {
    appExec(`bin/rails server -p ${APP_PORT}`);
    done();
  }));

  gulp.task('dev:console', (done) => {
    appExec('bin/rails console');
    done();
  });

  // --- テスト ---

  gulp.task('dev:test', (done) => {
    appExec('bundle exec rspec');
    done();
  });

  gulp.task('dev:test:domain', (done) => {
    appExec('bundle exec rspec spec --pattern "**/domain/**/*_spec.rb"');
    done();
  });

  gulp.task('tdd:backend', (done) => {
    appExec('bundle exec rspec --only-failures --fail-fast');
    done();
  });

  // --- 品質チェック ---

  gulp.task('dev:lint', (done) => {
    appExec('bundle exec rubocop');
    done();
  });

  gulp.task('dev:security', (done) => {
    appExec('bundle exec brakeman -q --no-pager');
    appExec('bundle exec bundler-audit check --update');
    done();
  });

  gulp.task('dev:arch', (done) => {
    appExec('bin/packwerk validate');
    appExec('bin/packwerk check');
    done();
  });

  gulp.task('dev:check', gulp.series('dev:lint', 'dev:security', 'dev:arch', 'dev:test'));

  // --- ヘルプ ---

  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発コマンド（apps/cargo-tracker） ===

  dev:server         DB 起動 + 開発サーバー起動（http://localhost:${APP_PORT}）
  dev:console        Rails コンソール

  dev:db:start       PostgreSQL コンテナを起動
  dev:db:stop        PostgreSQL コンテナを停止
  dev:db:logs        PostgreSQL のログを表示（直近 100 行）
  dev:db:connect     psql で開発 DB に接続
  dev:db:prepare     DB 作成 + マイグレーション + シード
  dev:db:migrate     マイグレーション実行

  dev:test           全テスト実行（SimpleCov カバレッジ付き）
  dev:test:domain    ドメイン層のユニットテストのみ実行
  tdd:backend        TDD モード（前回失敗のみ・fail-fast）

  dev:lint           RuboCop
  dev:security       Brakeman + bundler-audit
  dev:arch           Packwerk validate + check
  dev:check          品質チェック + 全テスト（コミット前推奨）

  dev:help           このヘルプを表示
  `);
    done();
  });
}
