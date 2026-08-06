'use strict';

import path from 'path';
import fs from 'fs';
import { execSync } from 'child_process';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

/** アプリケーションのディレクトリ */
const APP_DIR = process.env.APP_DIR || 'apps/cargo-tracker';

/** 既定の Spring プロファイル */
const DEFAULT_PROFILE = process.env.APP_PROFILE || 'local';

/** アプリケーションのポート */
function appPort() {
  return process.env.APP_PORT || '8080';
}

/** Adminer のポート */
function adminerPort() {
  return process.env.ADMINER_PORT || '8081';
}

/** アプリケーションディレクトリの絶対パス */
function appPath() {
  return path.join(process.cwd(), APP_DIR);
}

/**
 * Gradle ラッパーを実行する。
 *
 * ラッパーを使うのは、実行環境の Gradle バージョンに依存させないためである。
 * Nix が提供する Gradle は 8.14.3 だが、本プロジェクトは 9.2.1 で固定している。
 *
 * @param {string} args - gradlew に渡す引数
 * @param {Object} [options] - 追加オプション
 */
function gradle(args, options = {}) {
  const cwd = appPath();
  if (!fs.existsSync(path.join(cwd, 'gradlew'))) {
    throw new Error(
      `Gradle ラッパーが見つかりません: ${cwd}/gradlew\n` +
        `APP_DIR が正しいか確認してください（現在: ${APP_DIR}）`
    );
  }
  execSync(`./gradlew ${args}`, {
    cwd,
    stdio: 'inherit',
    env: cleanDockerEnv(),
    ...options,
  });
}

/**
 * Docker が必要なタスクの前提を確認する。
 *
 * ADR-003 により H2 を採用しないため、統合テストと開発サーバーの起動には
 * PostgreSQL が必要である。Docker が停止した状態で実行すると、
 * 原因の分かりにくいエラーで失敗するため事前に検出する。
 */
function requireDocker(taskName) {
  if (!isDockerAvailable()) {
    throw new Error(
      `${taskName} には Docker が必要です。Docker Desktop を起動してください。\n` +
        `本プロジェクトは H2 を採用していないため（ADR-003）、\n` +
        `統合テストと開発サーバーの起動には PostgreSQL コンテナが必要です。`
    );
  }
}

/** データベースコンテナを起動して healthy になるまで待つ */
function startDatabase() {
  execSync('docker compose up -d db adminer', {
    stdio: 'inherit',
    env: cleanDockerEnv(),
  });
  execSync('docker compose wait db --timeout 60 || true', {
    stdio: 'ignore',
    env: cleanDockerEnv(),
  });
}

// ============================================
// タスク定義
// ============================================

export default function appTasks(gulp) {
  // --- 開発 ---

  gulp.task('app:db', (done) => {
    requireDocker('app:db');
    startDatabase();
    console.log(`\nDB: localhost:5432  /  Adminer: http://localhost:${adminerPort()}/`);
    done();
  });

  gulp.task('app:start', (done) => {
    requireDocker('app:start');
    startDatabase();
    console.log(`\n開発サーバーを起動します（プロファイル: ${DEFAULT_PROFILE}）`);
    console.log(`  アプリ: http://localhost:${appPort()}/`);
    gradle(`bootRun --args='--spring.profiles.active=${DEFAULT_PROFILE}'`);
    done();
  });

  gulp.task('app:stop', (done) => {
    execSync('docker compose stop db adminer', {
      stdio: 'inherit',
      env: cleanDockerEnv(),
    });
    done();
  });

  gulp.task('app:open', (done) => {
    openUrl(`http://localhost:${appPort()}/`);
    done();
  });

  // --- テスト ---

  gulp.task('app:test', (done) => {
    requireDocker('app:test');
    gradle('test');
    done();
  });

  /**
   * TDD モード。
   * ソースの変更を検知してテストを再実行する（レッド → グリーン → リファクタリング）。
   */
  gulp.task('app:tdd', (done) => {
    requireDocker('app:tdd');
    console.log('TDD モードを開始します（Ctrl+C で終了）');
    gradle('test --continuous');
    done();
  });

  gulp.task('app:coverage', (done) => {
    requireDocker('app:coverage');
    gradle('test jacocoTestReport');
    const report = path.join(appPath(), 'build/reports/jacoco/test/html/index.html');
    console.log(`\nカバレッジレポート: ${report}`);
    done();
  });

  // --- 品質 ---

  gulp.task('app:lint', (done) => {
    gradle('checkstyleMain checkstyleTest spotbugsMain spotbugsTest');
    done();
  });

  gulp.task('app:check', (done) => {
    requireDocker('app:check');
    gradle('check');
    done();
  });

  // --- ビルド ---

  gulp.task('app:build', (done) => {
    requireDocker('app:build');
    gradle('build');
    done();
  });

  gulp.task('app:clean', (done) => {
    gradle('clean');
    done();
  });

  // --- 設計ドキュメント生成 ---

  /**
   * JIG でコードから設計ドキュメントを生成する。
   *
   * これは「テスト」ではない。生成物と docs/design を突き合わせて、
   * 設計と実装の乖離を人間が確認するための材料である。
   */
  gulp.task('app:jig', (done) => {
    gradle('clean jigReports');
    const index = path.join(appPath(), 'build/jig/index.html');
    console.log(`\nJIG ドキュメント: ${index}`);
    done();
  });

  gulp.task('app:jig:open', (done) => {
    const index = path.join(appPath(), 'build/jig/index.html');
    if (!fs.existsSync(index)) {
      throw new Error('JIG ドキュメントが未生成です。先に app:jig を実行してください。');
    }
    openUrl(`file://${index}`);
    done();
  });

  /**
   * jig-erd で実 DB スキーマから ER 図を生成する。
   *
   * Graphviz が必要。docs/design/data-model.md の ER 図は「設計」、
   * ここで生成されるのは Flyway が構築した「実装」である。
   */
  gulp.task('app:jig-erd', (done) => {
    requireDocker('app:jig-erd');
    try {
      execSync('dot -V', { stdio: 'ignore' });
    } catch {
      throw new Error(
        'jig-erd には Graphviz が必要です。`brew install graphviz` でインストールしてください。'
      );
    }
    gradle('jigErd');
    const dir = path.join(appPath(), 'build/jig-erd');
    console.log(`\nER 図: ${dir}`);
    done();
  });

  // --- ヘルプ ---

  gulp.task('app:help', (done) => {
    console.log(`
アプリケーションタスク（${APP_DIR}）

開発:
  app:db              DB / Adminer コンテナを起動
  app:start           開発サーバーを起動（DB 起動を含む）
  app:stop            DB / Adminer コンテナを停止
  app:open            アプリをブラウザで開く

テスト:
  app:test            テストを実行
  app:tdd             TDD モード（変更を検知して再実行）
  app:coverage        テスト + カバレッジレポート生成

品質:
  app:lint            静的解析のみ（Checkstyle / SpotBugs）
  app:check           テスト + 静的解析

ビルド:
  app:build           ビルド
  app:clean           ビルド成果物を削除

設計ドキュメント生成:
  app:jig             JIG でコードから設計ドキュメントを生成
  app:jig:open        JIG ドキュメントをブラウザで開く
  app:jig-erd         jig-erd で実スキーマから ER 図を生成（Graphviz 必要）

前提:
  Docker が必要です。本プロジェクトは H2 を採用していないため（ADR-003）、
  テストと開発サーバーの起動には PostgreSQL コンテナが必要です。

環境変数（.env に設定）:
  APP_DIR             アプリのディレクトリ（デフォルト: apps/cargo-tracker）
  APP_PROFILE         Spring プロファイル（デフォルト: local）
  APP_PORT            アプリのポート（デフォルト: 8080）
  ADMINER_PORT        Adminer のポート（デフォルト: 8081）

手順書:
  docs/operation/アプリケーション開発環境セットアップ手順書.md
`);
    done();
  });
}
