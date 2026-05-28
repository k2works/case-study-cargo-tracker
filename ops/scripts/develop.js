'use strict';

import path from 'path';
import readline from 'readline';
import { execSync, spawn, spawnSync } from 'child_process';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

// ============================================
// ヘルパー（破壊的操作の確認・smoke チェック）
// ============================================

/**
 * 破壊的操作の前に y/n 確認を取る。
 * 非対話環境（CI 等）では自動的に false を返す。
 */
function confirmDestructive(message) {
  if (!process.stdin.isTTY) {
    console.error(`${message} 非対話環境では実行を中断します。対話端末から再実行してください。`);
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
 * 指定エンドポイント群に curl でヘルスチェックを実行する。
 */
function runSmoke(targets, done) {
  const curlCmd = process.platform === 'win32' ? 'curl.exe' : 'curl';
  let failed = 0;
  for (const t of targets) {
    const result = spawnSync(
      curlCmd,
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
  { name: 'authms',     port: 8081, label: '認証サービス' },
  { name: 'bookingms',  port: 8082, label: '予約サービス' },
  { name: 'routingms',  port: 8083, label: '経路設計サービス' },
  { name: 'trackingms', port: 8084, label: '追跡サービス' },
  { name: 'handlingms', port: 8085, label: '荷役サービス' },
  { name: 'gatewayms',  port: 8080, label: 'API Gateway' },
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
  // バックエンド一括起動（local-docker）
  // ──────────────────────────────────────────

  /**
   * 全バックエンドサービスを local-docker プロファイルで並列起動
   * 起動順: authms → bookingms → routingms → trackingms → handlingms → gatewayms
   * （依存関係に配慮して 3 秒間隔。trackingms / handlingms は cross-service 購読のため
   * 発行元 bookingms / routingms の後に起動する）
   */
  gulp.task('dev:backend:start:docker', (done) => {
    if (!isDockerAvailable()) {
      console.error('Docker が起動していません。先に dev:infra:start を実行してください。');
      process.exit(1);
    }

    const profile = 'local-docker';
    const startOrder = ['authms', 'bookingms', 'routingms', 'trackingms', 'handlingms', 'gatewayms'];
    const delay = 3000;

    console.log('[dev:backend:start:docker] local-docker プロファイルでバックエンドサービスを起動します...');

    startOrder.forEach((name, i) => {
      const svc = SERVICES.find(s => s.name === name);
      setTimeout(() => {
        console.log(`[${svc.label}] 起動中... (port ${svc.port})`);
        const proc = spawn(
          './gradlew',
          [`:${name}:bootRun`, `--args=--spring.profiles.active=${profile}`],
          { cwd: BACKEND_DIR, stdio: 'inherit', shell: false }
        );
        proc.on('error', (err) => console.error(`[${svc.label}] 起動エラー: ${err.message}`));
      }, i * delay);
    });

    const totalWait = startOrder.length * delay + 1000;
    setTimeout(done, totalWait);
  });

  // ──────────────────────────────────────────
  // local-docker プロファイル運用タスク
  //
  // docker-compose.yml と application-local-docker.yml で構成する
  // フルスタック（Kafka + ZooKeeper + PostgreSQL + authms + routingms +
  // gatewayms）の構築・起動・停止・リセット・疎通確認を提供する。
  // ──────────────────────────────────────────

  /**
   * 全サービスの Docker イメージをビルド
   */
  gulp.task('local-docker:build', (done) => {
    if (!isDockerAvailable()) {
      console.error('Docker が起動していません。Docker Desktop を起動してください。');
      process.exit(1);
    }
    compose('build');
    done();
  });

  /**
   * 全サービスを起動（Kafka + ZooKeeper + PostgreSQL + authms + routingms + gatewayms）
   */
  gulp.task('local-docker:up', (done) => {
    if (!isDockerAvailable()) {
      console.error('Docker が起動していません。Docker Desktop を起動してください。');
      process.exit(1);
    }
    compose('up -d');
    done();
  });

  /**
   * コンテナとネットワークを停止・削除（ボリュームは保持）
   */
  gulp.task('local-docker:down', (done) => {
    compose('down');
    done();
  });

  /**
   * ボリュームを含めた完全リセット（Kafka / PostgreSQL データを破棄）
   * 実行前に対話的に y/n 確認を取る。
   */
  gulp.task('local-docker:clean', async (done) => {
    const ok = await confirmDestructive(
      'local-docker:clean は Kafka / PostgreSQL データを完全削除します。続行しますか？',
    );
    if (!ok) {
      console.log('local-docker:clean をキャンセルしました。');
      done();
      return;
    }
    compose('down -v');
    done();
  });

  /**
   * authms / bookingms / routingms / trackingms / handlingms / gatewayms の
   * health エンドポイント疎通確認
   */
  gulp.task('local-docker:smoke', (done) => {
    const targets = [
      { name: 'authms',     url: 'http://localhost:8081/actuator/health' },
      { name: 'bookingms',  url: 'http://localhost:8082/actuator/health' },
      { name: 'routingms',  url: 'http://localhost:8083/actuator/health' },
      { name: 'trackingms', url: 'http://localhost:8084/actuator/health' },
      { name: 'handlingms', url: 'http://localhost:8085/actuator/health' },
      { name: 'gatewayms',  url: 'http://localhost:8080/actuator/health' },
    ];
    runSmoke(targets, done);
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
  dev:backend:build              全サービスをコンパイル（local-h2）
  dev:backend:test               全サービスのテストを実行（local-h2）
  dev:backend:check              全サービスの品質チェック（Checkstyle + SpotBugs + テスト）
  dev:backend:coverage           テストカバレッジレポートを生成
  dev:backend:start:docker       全サービスを local-docker プロファイルで一括起動

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

【local-docker プロファイル（フルスタック）】
  local-docker:build        全サービスの Docker イメージをビルド
  local-docker:up           全サービス起動（インフラ + authms + routingms + gatewayms）
  local-docker:down         コンテナ停止（ボリュームは保持）
  local-docker:clean        完全リセット（実行前に y/n 確認、Kafka / DB データを破棄）
  local-docker:smoke        authms / routingms / gatewayms の health 疎通確認

【セットアップ確認】
  dev:setup:verify          ビルド → テスト → 型チェック 一括確認

  dev:help                  このヘルプを表示
    `);
    done();
  });
}
