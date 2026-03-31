'use strict';

import path from 'path';
import { execSync } from 'child_process';
import { cleanDockerEnv, isDockerAvailable } from './shared.js';

// ============================================
// 設定
// ============================================

/** アプリケーションルートディレクトリ */
const APP_DIR = path.join(process.cwd(), 'apps', 'cargo-tracker');

/** PostgreSQL サービス名（docker-compose.yml に合わせる） */
const DB_SERVICE = 'postgres';

// ============================================
// ヘルパー関数
// ============================================

/**
 * Gradle コマンドを実行する
 * @param {string} args - Gradle タスクおよびオプション
 */
function gradle(args) {
  const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
  execSync(`${gradlew} ${args}`, { cwd: APP_DIR, stdio: 'inherit' });
}

/**
 * Docker Compose コマンドを実行する
 * @param {string} args - docker compose に渡す引数
 */
function dockerCompose(args) {
  execSync(`docker compose ${args}`, { stdio: 'inherit', env: cleanDockerEnv() });
}

/**
 * Docker が利用可能か確認し、不可なら警告を出して false を返す
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

  // --------------------------------------------
  // バックエンドタスク
  // --------------------------------------------

  gulp.task('dev:backend:start', (done) => {
    try {
      console.log('Starting cargo-tracker (default profile / H2)...');
      console.log('URL: http://localhost:8080');
      gradle('bootRun');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:backend:start:product', (done) => {
    try {
      console.log('Starting cargo-tracker (product profile / PostgreSQL)...');
      console.log('URL: http://localhost:8080');
      gradle('bootRun --args="--spring.profiles.active=product"');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:backend:tdd', (done) => {
    try {
      console.log('Starting TDD mode (./gradlew test --continuous)...');
      console.log('Press Ctrl+D to stop.');
      gradle('test --continuous');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:backend:build', (done) => {
    try {
      console.log('Building cargo-tracker...');
      gradle('build');
      console.log('Build completed.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:backend:check', (done) => {
    try {
      console.log('Running quality checks (Checkstyle, SpotBugs, tests)...');
      gradle('check');
      console.log('All checks passed.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:backend:clean', (done) => {
    try {
      console.log('Cleaning build artifacts...');
      gradle('clean');
      console.log('Clean completed.');
      done();
    } catch (error) {
      done(error);
    }
  });

  // --------------------------------------------
  // データベースタスク
  // --------------------------------------------

  gulp.task('dev:db:start', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log('Starting PostgreSQL container...');
      dockerCompose(`up -d ${DB_SERVICE}`);
      console.log('PostgreSQL is running on localhost:5432');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:db:stop', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log('Stopping PostgreSQL container...');
      dockerCompose(`stop ${DB_SERVICE}`);
      console.log('PostgreSQL stopped.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:db:logs', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      dockerCompose(`logs -f ${DB_SERVICE}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // --------------------------------------------
  // ヘルプ
  // --------------------------------------------

  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発コマンド ===

  バックエンド:
    dev:backend:start           Spring Boot 起動（default / H2）
    dev:backend:start:product   Spring Boot 起動（product / PostgreSQL）
    dev:backend:tdd             テスト自動再実行（--continuous）
    dev:backend:build           ビルド（./gradlew build）
    dev:backend:check           品質チェック（Checkstyle, SpotBugs, テスト）
    dev:backend:clean           ビルド成果物を削除

  データベース:
    dev:db:start                PostgreSQL コンテナ起動（ポート 5432）
    dev:db:stop                 PostgreSQL コンテナ停止
    dev:db:logs                 PostgreSQL ログを表示

  ヘルプ:
    dev:help                    このヘルプを表示
    `);
    done();
  });
}
