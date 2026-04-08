'use strict';

import { execSync } from 'child_process';
import { cleanDockerEnv, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

const PREFIX = 'DEV';
const CARGO_TRACKER_DIR = 'apps/cargo-tracker';

/**
 * Heroku アプリ名を取得する
 * @returns {string} Heroku アプリ名
 */
const herokuAppName = () => process.env[`${PREFIX}_HEROKU_APP_NAME`] || '';

/**
 * Docker ビルドプラットフォームを取得する（デフォルト: linux/amd64）
 * @returns {string} Docker プラットフォーム
 */
const dockerPlatform = () => process.env[`${PREFIX}_DOCKER_PLATFORM`] || 'linux/amd64';

// ============================================
// ヘルパー関数
// ============================================

/**
 * Heroku アプリ名が設定されているか確認する
 * @throws {Error} DEV_HEROKU_APP_NAME が未設定の場合
 */
function requireAppName() {
  const app = herokuAppName();
  if (!app) {
    throw new Error('DEV_HEROKU_APP_NAME を .env に設定してください');
  }
  return app;
}

/**
 * Docker イメージタグを生成する
 * @param {string} app - Heroku アプリ名
 * @returns {string} イメージタグ
 */
function imageTag(app) {
  return `registry.heroku.com/${app}/web`;
}

/**
 * Heroku CLI コマンドを実行する
 * @param {string} command - heroku コマンド（heroku 以降の部分）
 * @param {object} [options] - execSync オプション
 */
function heroku(command, options = {}) {
  execSync(`heroku ${command}`, { stdio: 'inherit', env: cleanDockerEnv(), ...options });
}

/**
 * Docker コマンドを実行する
 * @param {string} command - docker コマンド（docker 以降の部分）
 * @param {object} [options] - execSync オプション
 */
function docker(command, options = {}) {
  execSync(`docker ${command}`, { stdio: 'inherit', env: cleanDockerEnv(), ...options });
}

// ============================================
// Gulp タスク
// ============================================

/**
 * 開発環境デプロイ（Heroku Container Registry）用 Gulp タスクを登録する
 * @param {import('gulp')} gulp
 */
export default function deployDevTasks(gulp) {

  // Heroku CLI にログイン（ブラウザ認証が必要なため手動実行用）
  // 自動化不可: heroku login はブラウザを開くため execSync では使用できない
  // 手動実行: ! heroku login
  gulp.task('deploy:dev:login', (done) => {
    console.log('deploy:dev:login は手動実行が必要なタスクですコロ助。');
    console.log('ターミナルで以下を実行してください:');
    console.log('  heroku login');
    console.log('  heroku container:login');
    done();
  });

  // Heroku Container Registry に Docker 認証情報を設定（非インタラクティブ）
  // heroku login 済みであることが前提
  gulp.task('deploy:dev:container:login', (done) => {
    console.log('Heroku Container Registry にログインします...');
    try {
      heroku('container:login');
      done();
    } catch (e) {
      done(e);
    }
  });

  // Heroku アプリを作成（container stack）
  gulp.task('deploy:dev:app:create', (done) => {
    const app = requireAppName();
    console.log(`Heroku アプリ "${app}" を作成します...`);
    try {
      heroku(`create ${app} --stack container`);
      done();
    } catch (e) {
      done(e);
    }
  });

  // Config Vars を設定
  gulp.task('deploy:dev:config', (done) => {
    const app = requireAppName();
    console.log(`Config Vars を設定します (${app})...`);
    try {
      heroku(`config:set JAVA_OPTS="-XX:MaxRAMPercentage=75.0" -a ${app}`);
      done();
    } catch (e) {
      done(e);
    }
  });

  // Docker イメージをビルド（linux/amd64 固定）
  gulp.task('deploy:dev:build', (done) => {
    const app = requireAppName();
    const platform = dockerPlatform();
    const tag = imageTag(app);
    console.log(`Docker イメージをビルドします (platform: ${platform}, tag: ${tag})...`);
    try {
      docker(`build --platform ${platform} -t ${tag} .`, { cwd: CARGO_TRACKER_DIR });
      done();
    } catch (e) {
      done(e);
    }
  });

  // Docker イメージを Heroku Container Registry に push
  gulp.task('deploy:dev:push', (done) => {
    const app = requireAppName();
    const tag = imageTag(app);
    console.log(`Docker イメージを push します (${tag})...`);
    try {
      docker(`push ${tag}`);
      done();
    } catch (e) {
      done(e);
    }
  });

  // Heroku にリリース
  gulp.task('deploy:dev:release', (done) => {
    const app = requireAppName();
    console.log(`Heroku にリリースします (${app})...`);
    try {
      heroku(`container:release web -a ${app}`);
      done();
    } catch (e) {
      done(e);
    }
  });

  // アプリをブラウザで開く
  gulp.task('deploy:dev:open', (done) => {
    const app = requireAppName();
    console.log(`アプリを開きます (${app})...`);
    try {
      heroku(`open -a ${app}`);
      done();
    } catch (e) {
      // heroku open が失敗した場合は URL を直接開く
      openUrl(`https://${app}.herokuapp.com`);
      done();
    }
  });

  // ログを表示（tail）
  gulp.task('deploy:dev:logs', (done) => {
    const app = requireAppName();
    console.log(`ログを表示します (${app})...`);
    try {
      heroku(`logs --tail -a ${app}`);
      done();
    } catch (e) {
      done(e);
    }
  });

  // デプロイ状態を確認
  gulp.task('deploy:dev:status', (done) => {
    const app = requireAppName();
    console.log(`デプロイ状態を確認します (${app})...`);
    try {
      heroku(`ps -a ${app}`);
      heroku(`config -a ${app}`);
      done();
    } catch (e) {
      done(e);
    }
  });

  // 更新デプロイ（container:login → build → push → release）
  // 前提: heroku login 済みであること（! heroku login で手動実行）
  gulp.task('deploy:dev', gulp.series(
    'deploy:dev:container:login',
    'deploy:dev:build',
    'deploy:dev:push',
    'deploy:dev:release',
  ));

  // 初回セットアップ（container:login → app:create → config → build → push → release）
  // 前提: heroku login 済みであること（! heroku login で手動実行）
  gulp.task('deploy:dev:setup', gulp.series(
    'deploy:dev:container:login',
    'deploy:dev:app:create',
    'deploy:dev:config',
    'deploy:dev:build',
    'deploy:dev:push',
    'deploy:dev:release',
  ));

  // ヘルプ
  gulp.task('deploy:dev:help', (done) => {
    console.log(`
=== 開発環境デプロイコマンド (Heroku Container Registry) ===

【前提】heroku login は自動化不可。初回のみ手動実行してください:
  ! heroku login

  deploy:dev:container:login  Heroku Container Registry に Docker 認証情報を設定
  deploy:dev:login        ログイン手順の案内を表示（手動実行が必要なため）
  deploy:dev:app:create   Heroku アプリを作成（container stack）
  deploy:dev:config       Config Vars を設定
  deploy:dev:build        Docker イメージをビルド（linux/amd64）
  deploy:dev:push         Docker イメージを Heroku Container Registry に push
  deploy:dev:release      Heroku にリリース
  deploy:dev:open         アプリをブラウザで開く
  deploy:dev:logs         ログを表示（tail）
  deploy:dev:status       デプロイ状態を確認
  deploy:dev              更新デプロイ（container:login → build → push → release）
  deploy:dev:setup        初回セットアップ（container:login → app:create → ... → release）
  deploy:dev:help         このヘルプを表示

必要な環境変数 (.env):
  DEV_HEROKU_APP_NAME     Heroku アプリ名（必須）
  DEV_DOCKER_PLATFORM     Docker プラットフォーム（省略時: linux/amd64）

使用例:
  初回: ! heroku login  →  npx gulp deploy:dev:setup
  更新: npx gulp deploy:dev
    `);
    done();
  });
}
