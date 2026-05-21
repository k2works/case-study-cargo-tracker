'use strict';

import path from 'path';
import { execSync, spawn } from 'child_process';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

const ROOT = path.resolve(process.cwd());
const BACKEND_DIR = path.join(ROOT, 'apps', 'backend');
const FRONTEND_DIR = path.join(ROOT, 'apps', 'frontend');
const COMPOSE_FILE = path.join(ROOT, 'apps', 'docker-compose.yml');
const ENV_FILE = path.join(ROOT, '.env');

/** バックエンドサービス定義 */
const SERVICES = [
  { name: 'authms',    port: 8081, label: '認証サービス' },
  { name: 'routingms', port: 8083, label: '経路設計サービス' },
  { name: 'gatewayms', port: 8080, label: 'API Gateway' },
];

/** Docker Compose サービス名 */
const DOCKER_SERVICES = {
  postgres: 'postgres',
  kafka:    'kafka',
  zookeeper: 'zookeeper',
};

// ============================================
// ヘルパー関数
// ============================================

/**
 * Gradle タスクを実行する
 * @param {string} task - Gradle タスク名（例: ':authms:test'）
 * @param {object} [opts] - オプション
 * @param {string} [opts.profile='local-h2'] - Spring Profiles Active
 * @param {string[]} [opts.jvmArgs] - 追加 JVM 引数
 */
function gradle(task, opts = {}) {
  const profile = opts.profile || 'local-h2';
  const jvmArgs = opts.jvmArgs || [];
  const extra = jvmArgs.length > 0 ? ` -Dspring.profiles.active=${profile} ${jvmArgs.join(' ')}` : '';
  const cmd = `./gradlew ${task} -Dspring.profiles.active=${profile}${extra}`;
  console.log(`[Gradle] ${cmd}`);
  execSync(cmd, { cwd: BACKEND_DIR, stdio: 'inherit' });
}

/**
 * Docker Compose コマンドを実行する
 * @param {string} subcommand - compose サブコマンド（例: 'up -d kafka'）
 */
function compose(subcommand) {
  const cmd = `docker compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE} ${subcommand}`;
  console.log(`[Docker Compose] ${cmd}`);
  execSync(cmd, { stdio: 'inherit', env: cleanDockerEnv() });
}

/**
 * npm コマンドをフロントエンドディレクトリで実行する
 * @param {string} script - npm スクリプト名
 */
function npm(script) {
  const cmd = `npm run ${script}`;
  console.log(`[npm] ${cmd}`);
  execSync(cmd, { cwd: FRONTEND_DIR, stdio: 'inherit' });
}

// ============================================
// Gulp タスク
// ============================================

export default function(gulp) {

  // ──────────────────────────────────────────
  // バックエンドビルド
  // ──────────────────────────────────────────

  /**
   * 全バックエンドサービスをコンパイル（local-h2）
   */
  gulp.task('dev:backend:build', (done) => {
    gradle('compileJava');
    done();
  });

  /**
   * 全バックエンドサービスのテスト実行（local-h2 プロファイル）
   */
  gulp.task('dev:backend:test', (done) => {
    gradle('test');
    done();
  });

  // サービス個別のテストタスク
  SERVICES.forEach((svc) => {
    /**
     * 特定サービスのテストを実行（local-h2 プロファイル）
     */
    gulp.task(`dev:${svc.name}:test`, (done) => {
      gradle(`:${svc.name}:test`);
      done();
    });

    /**
     * 特定サービスを local-h2 プロファイルで起動
     */
    gulp.task(`dev:${svc.name}:start`, (done) => {
      gradle(`:${svc.name}:bootRun`, { profile: 'local-h2' });
      done();
    });

    /**
     * 特定サービスを local-docker プロファイルで起動（Docker が必要）
     */
    gulp.task(`dev:${svc.name}:start:docker`, (done) => {
      gradle(`:${svc.name}:bootRun`, { profile: 'local-docker' });
      done();
    });

    /**
     * TDD モード: 特定サービスのテストを継続監視実行
     */
    gulp.task(`tdd:${svc.name}`, (done) => {
      const cmd = `./gradlew :${svc.name}:test --continuous -Dspring.profiles.active=local-h2`;
      console.log(`[TDD] ${cmd}`);
      const proc = spawn('./gradlew', [`:${svc.name}:test`, '--continuous', '-Dspring.profiles.active=local-h2'], {
        cwd: BACKEND_DIR,
        stdio: 'inherit',
        shell: false,
      });
      proc.on('close', done);
    });
  });

  // ──────────────────────────────────────────
  // フロントエンド
  // ──────────────────────────────────────────

  /**
   * フロントエンド依存パッケージのインストール
   */
  gulp.task('dev:frontend:install', (done) => {
    execSync('npm install', { cwd: FRONTEND_DIR, stdio: 'inherit' });
    done();
  });

  /**
   * フロントエンド開発サーバーを起動（port 5173）
   */
  gulp.task('dev:frontend:start', (done) => {
    npm('dev');
    done();
  });

  /**
   * フロントエンド TypeScript 型チェック
   */
  gulp.task('dev:frontend:typecheck', (done) => {
    execSync('npx tsc --noEmit', { cwd: FRONTEND_DIR, stdio: 'inherit' });
    done();
  });

  /**
   * フロントエンド ESLint チェック
   */
  gulp.task('dev:frontend:lint', (done) => {
    npm('lint');
    done();
  });

  /**
   * フロントエンドビルド（本番用）
   */
  gulp.task('dev:frontend:build', (done) => {
    npm('build');
    done();
  });

  // ──────────────────────────────────────────
  // Docker Compose（local-docker 用インフラ）
  // ──────────────────────────────────────────

  /**
   * PostgreSQL のみ起動（local-docker プロファイル用）
   */
  gulp.task('dev:db:start', (done) => {
    if (!isDockerAvailable()) {
      console.error('Docker が起動していません。Docker Desktop を起動してください。');
      process.exit(1);
    }
    compose(`up -d ${DOCKER_SERVICES.postgres}`);
    done();
  });

  /**
   * PostgreSQL を停止
   */
  gulp.task('dev:db:stop', (done) => {
    compose(`stop ${DOCKER_SERVICES.postgres}`);
    done();
  });

  /**
   * Kafka + ZooKeeper + PostgreSQL をすべて起動
   */
  gulp.task('dev:infra:start', (done) => {
    if (!isDockerAvailable()) {
      console.error('Docker が起動していません。Docker Desktop を起動してください。');
      process.exit(1);
    }
    compose('up -d');
    done();
  });

  /**
   * Kafka + ZooKeeper + PostgreSQL をすべて停止
   */
  gulp.task('dev:infra:stop', (done) => {
    compose('down');
    done();
  });

  /**
   * インフラコンテナの状態を確認
   */
  gulp.task('dev:infra:status', (done) => {
    compose('ps');
    done();
  });

  /**
   * インフラコンテナのログを表示
   */
  gulp.task('dev:infra:logs', (done) => {
    compose('logs --tail=100');
    done();
  });

  // ──────────────────────────────────────────
  // 品質チェック
  // ──────────────────────────────────────────

  /**
   * バックエンド全サービスの品質チェック（Checkstyle + SpotBugs + テスト）
   */
  gulp.task('dev:backend:check', (done) => {
    gradle('check');
    done();
  });

  /**
   * テストカバレッジレポートを生成
   */
  gulp.task('dev:backend:coverage', (done) => {
    gradle('test jacocoTestReport');
    done();
  });

  // ──────────────────────────────────────────
  // セットアップ確認
  // ──────────────────────────────────────────

  /**
   * 開発環境セットアップ確認（ビルド → テスト → フロントエンド型チェック）
   */
  gulp.task('dev:setup:verify', gulp.series(
    'dev:backend:build',
    'dev:backend:test',
    'dev:frontend:typecheck',
  ));

  // ──────────────────────────────────────────
  // ヘルプ
  // ──────────────────────────────────────────

  /**
   * アプリケーション開発タスク一覧を表示
   */
  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発タスク ===

【バックエンド（全体）】
  dev:backend:build         全サービスをコンパイル（local-h2）
  dev:backend:test          全サービスのテストを実行（local-h2）
  dev:backend:check         全サービスの品質チェック（Checkstyle + SpotBugs + テスト）
  dev:backend:coverage      テストカバレッジレポートを生成

【バックエンド（サービス個別）】
  dev:authms:test           authms テスト実行
  dev:authms:start          authms 起動（local-h2）
  dev:authms:start:docker   authms 起動（local-docker）
  dev:routingms:test        routingms テスト実行
  dev:routingms:start       routingms 起動（local-h2）
  dev:routingms:start:docker routingms 起動（local-docker）
  dev:gatewayms:test        gatewayms テスト実行
  dev:gatewayms:start       gatewayms 起動（local-h2）

【TDD モード（継続テスト監視）】
  tdd:authms                authms TDD モード（ファイル変更監視）
  tdd:routingms             routingms TDD モード
  tdd:gatewayms             gatewayms TDD モード

【フロントエンド】
  dev:frontend:install      npm install 実行
  dev:frontend:start        開発サーバー起動（port 5173）
  dev:frontend:typecheck    TypeScript 型チェック
  dev:frontend:lint         ESLint チェック
  dev:frontend:build        本番ビルド

【インフラ（Docker Compose）】
  dev:db:start              PostgreSQL のみ起動
  dev:db:stop               PostgreSQL 停止
  dev:infra:start           Kafka + ZooKeeper + PostgreSQL 全起動
  dev:infra:stop            全インフラ停止
  dev:infra:status          コンテナ状態確認
  dev:infra:logs            コンテナログ表示

【セットアップ確認】
  dev:setup:verify          ビルド → テスト → 型チェック 一括確認

  dev:help                  このヘルプを表示
    `);
    done();
  });
}
