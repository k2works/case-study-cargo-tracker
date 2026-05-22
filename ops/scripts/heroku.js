'use strict';

import { execSync, spawnSync } from 'child_process';
import path from 'path';

const ROOT = path.resolve(process.cwd());
const BACKEND_DIR = path.join(ROOT, 'apps', 'backend');

/** Heroku アプリ定義 */
const APPS = [
  { name: 'cargo-tracker-5-authms',    service: 'authms',    port: 8081, label: '認証サービス' },
  { name: 'cargo-tracker-5-routingms', service: 'routingms', port: 8083, label: '経路設計サービス' },
  { name: 'cargo-tracker-5-gatewayms', service: 'gatewayms', port: 8080, label: 'API Gateway' },
];

// ゲートウェイは最後にデプロイ（依存サービスが先に起動している必要がある）
const DEPLOY_ORDER = ['authms', 'routingms', 'gatewayms'];

/**
 * Heroku CLI コマンドを実行する
 * @param {string} cmd
 */
function heroku(cmd) {
  console.log(`[Heroku] ${cmd}`);
  execSync(`heroku ${cmd}`, { stdio: 'inherit' });
}

/**
 * Container Registry にイメージを push してリリースする
 * @param {string} appName - Heroku アプリ名
 * @param {string} service - サービス名（Dockerfile のあるディレクトリ名）
 */
function containerDeploy(appName, service) {
  const contextDir = BACKEND_DIR;
  const dockerfile = path.join(BACKEND_DIR, service, 'Dockerfile');
  console.log(`\n[${appName}] Container デプロイ開始...`);
  execSync(
    `heroku container:push web --app ${appName} --context-path ${contextDir} --dockerfile ${dockerfile}`,
    { stdio: 'inherit' }
  );
  execSync(`heroku container:release web --app ${appName}`, { stdio: 'inherit' });
  console.log(`[${appName}] ✅ デプロイ完了`);
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {

  // ──────────────────────────────────────────
  // Stack 設定
  // ──────────────────────────────────────────

  /**
   * 全アプリの Stack を container に設定する（初回のみ実行）
   */
  gulp.task('heroku:stack:set', (done) => {
    APPS.forEach(({ name }) => {
      console.log(`[heroku:stack:set] ${name}`);
      execSync(`heroku stack:set container --app ${name}`, { stdio: 'inherit' });
    });
    done();
  });

  // ──────────────────────────────────────────
  // デプロイ（サービス個別）
  // ──────────────────────────────────────────

  APPS.forEach(({ name, service, label }) => {
    /**
     * 特定サービスを Heroku にデプロイ
     */
    gulp.task(`heroku:deploy:${service}`, (done) => {
      containerDeploy(name, service);
      done();
    });
  });

  // ──────────────────────────────────────────
  // デプロイ（全サービス順次）
  // ──────────────────────────────────────────

  /**
   * 全バックエンドサービスを順次デプロイ（authms → routingms → gatewayms）
   */
  gulp.task('heroku:deploy', gulp.series(
    ...DEPLOY_ORDER.map(svc => `heroku:deploy:${svc}`)
  ));

  // ──────────────────────────────────────────
  // ステータス確認
  // ──────────────────────────────────────────

  /**
   * 全アプリの稼働状態を確認
   */
  gulp.task('heroku:status', (done) => {
    APPS.forEach(({ name, label }) => {
      console.log(`\n--- ${label} (${name}) ---`);
      try {
        execSync(`heroku ps --app ${name}`, { stdio: 'inherit' });
      } catch {
        console.error(`[${name}] 状態取得失敗`);
      }
    });
    done();
  });

  /**
   * 全アプリのログを表示（直近 50 行）
   */
  gulp.task('heroku:logs', (done) => {
    APPS.forEach(({ name, label }) => {
      console.log(`\n=== ${label} (${name}) ===`);
      try {
        execSync(`heroku logs --tail=false --num=50 --app ${name}`, { stdio: 'inherit' });
      } catch {
        console.error(`[${name}] ログ取得失敗`);
      }
    });
    done();
  });

  // ──────────────────────────────────────────
  // ヘルスチェック
  // ──────────────────────────────────────────

  /**
   * 全アプリの /actuator/health を確認
   */
  gulp.task('heroku:health', (done) => {
    const urls = {
      authms:    'https://cargo-tracker-5-authms-0b03518393c9.herokuapp.com/actuator/health',
      routingms: 'https://cargo-tracker-5-routingms-06b1dc236f74.herokuapp.com/actuator/health',
      gatewayms: 'https://cargo-tracker-5-gatewayms-87d9e927d00b.herokuapp.com/actuator/health',
    };
    Object.entries(urls).forEach(([svc, url]) => {
      console.log(`\n[${svc}] ${url}`);
      try {
        execSync(`curl -sf ${url}`, { stdio: 'inherit' });
        console.log(`\n✅ ${svc} 正常`);
      } catch {
        console.error(`❌ ${svc} 応答なし`);
      }
    });
    done();
  });

  // ──────────────────────────────────────────
  // 再起動・停止
  // ──────────────────────────────────────────

  /**
   * 全アプリを再起動
   */
  gulp.task('heroku:restart', (done) => {
    APPS.forEach(({ name }) => {
      heroku(`restart --app ${name}`);
    });
    done();
  });

  /**
   * 全アプリの dyno を停止（無料化のためスケールダウン）
   */
  gulp.task('heroku:stop', (done) => {
    APPS.forEach(({ name }) => {
      console.log(`[heroku:stop] ${name}`);
      execSync(`heroku ps:scale web=0 --app ${name}`, { stdio: 'inherit' });
    });
    done();
  });

  /**
   * 全アプリの dyno を起動（スケールアップ）
   */
  gulp.task('heroku:start', (done) => {
    APPS.forEach(({ name }) => {
      console.log(`[heroku:start] ${name}`);
      execSync(`heroku ps:scale web=1 --app ${name}`, { stdio: 'inherit' });
    });
    done();
  });

  // ──────────────────────────────────────────
  // 環境変数管理
  // ──────────────────────────────────────────

  /**
   * 全アプリの環境変数一覧を表示
   */
  gulp.task('heroku:config', (done) => {
    APPS.forEach(({ name, label }) => {
      console.log(`\n--- ${label} (${name}) ---`);
      execSync(`heroku config --app ${name}`, { stdio: 'inherit' });
    });
    done();
  });

  // ──────────────────────────────────────────
  // ヘルプ
  // ──────────────────────────────────────────

  /**
   * Heroku タスク一覧を表示
   */
  gulp.task('heroku:help', (done) => {
    console.log(`
=== Heroku デプロイタスク ===

【初期セットアップ（初回のみ）】
  heroku:stack:set          全アプリの Stack を container に設定

【デプロイ】
  heroku:deploy             全サービスを順次デプロイ（authms → routingms → gatewayms）
  heroku:deploy:authms      authms のみデプロイ
  heroku:deploy:routingms   routingms のみデプロイ
  heroku:deploy:gatewayms   gatewayms のみデプロイ

【稼働確認】
  heroku:status             全アプリの dyno 状態を確認
  heroku:health             全アプリの /actuator/health を確認
  heroku:logs               全アプリのログを表示（直近 50 行）
  heroku:config             全アプリの環境変数一覧を表示

【制御】
  heroku:start              全アプリの dyno を起動（web=1）
  heroku:stop               全アプリの dyno を停止（web=0）
  heroku:restart            全アプリを再起動

  heroku:help               このヘルプを表示
    `);
    done();
  });
}
