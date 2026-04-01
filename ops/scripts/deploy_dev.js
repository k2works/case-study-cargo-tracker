'use strict';

import path from 'path';
import { execSync } from 'child_process';
import { cleanDockerEnv } from './shared.js';

// ============================================
// 設定
// ============================================

const PREFIX = 'DEV';

/** Heroku web プロセス定義 */
const SERVICES = [
  { name: 'web', processType: 'web', label: 'Cargo Tracker Web' },
];

/** アプリケーションルート */
const APP_DIR = path.join(process.cwd(), 'apps', 'cargo-tracker');

// ============================================
// ヘルパー関数
// ============================================

/**
 * 必須環境変数を取得する
 * @param {string} name - 環境変数名
 * @returns {string} 環境変数値
 */
function requiredEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} を .env に設定してください`);
  }
  return value;
}

/**
 * Heroku アプリ名を返す
 * @returns {string} Heroku アプリ名
 */
function herokuAppName() {
  return requiredEnv(`${PREFIX}_HEROKU_APP_NAME`);
}

/**
 * Docker build platform を返す
 * @returns {string} Docker build platform
 */
function dockerPlatform() {
  return process.env[`${PREFIX}_DOCKER_PLATFORM`] || 'linux/amd64';
}

/**
 * Heroku CLI コマンドを実行する
 * @param {string} args - heroku に渡す引数
 * @param {object} [options] - オプション
 * @param {string} [options.cwd] - 作業ディレクトリ
 */
function heroku(args, options = {}) {
  execSync(`heroku ${args}`, {
    stdio: 'inherit',
    env: cleanDockerEnv(),
    ...(options.cwd ? { cwd: options.cwd } : {}),
  });
}

/**
 * Docker コマンドを実行する
 * @param {string} args - docker に渡す引数
 * @param {object} [options] - オプション
 * @param {string} [options.cwd] - 作業ディレクトリ
 */
function docker(args, options = {}) {
  execSync(`docker ${args}`, {
    stdio: 'inherit',
    env: cleanDockerEnv(),
    ...(options.cwd ? { cwd: options.cwd } : {}),
  });
}

/**
 * Heroku Container Registry のイメージ名を返す
 * @param {{processType: string}} service - サービス定義
 * @returns {string} イメージ名
 */
function imageName(service) {
  return `registry.heroku.com/${herokuAppName()}/${service.processType}`;
}

/**
 * Heroku CLI のログインを確認する
 */
function ensureHerokuLogin() {
  heroku('auth:whoami');
}

/**
 * Heroku アプリが存在することを確認する
 */
function ensureHerokuAppExists() {
  execSync(`heroku apps:info -a ${herokuAppName()}`, {
    stdio: 'ignore',
    env: cleanDockerEnv(),
  });
}

/**
 * Heroku API token を取得する
 * @returns {string} Heroku API token
 */
function herokuAuthToken() {
  const output = execSync('heroku auth:token', {
    encoding: 'utf8',
    env: cleanDockerEnv(),
  });
  const token = output
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line.startsWith('HRKU-'));

  if (!token) {
    throw new Error('Heroku API token を取得できませんでした');
  }

  return token;
}

/**
 * Heroku Container Registry に Docker login する
 */
function dockerLoginToHerokuRegistry() {
  const token = herokuAuthToken();
  execSync('docker login --username=_ --password-stdin registry.heroku.com', {
    stdio: ['pipe', 'inherit', 'inherit'],
    env: cleanDockerEnv(),
    input: `${token}\n`,
  });
}

/**
 * ローカル Docker イメージを build する
 * @param {{processType: string, label: string}} service - サービス定義
 */
function buildImage(service) {
  docker(
    `build --platform ${dockerPlatform()} --provenance=false --sbom=false -t ${imageName(service)} .`,
    { cwd: APP_DIR },
  );
}

/**
 * ローカル Docker イメージを Heroku Registry に push する
 * @param {{processType: string}} service - サービス定義
 */
function pushImage(service) {
  docker(`push ${imageName(service)}`);
}

/**
 * Heroku にリリースする
 * @param {{processType: string}} service - サービス定義
 */
function releaseImage(service) {
  heroku(`container:release ${service.processType} -a ${herokuAppName()}`);
}

// ============================================
// Gulp タスク
// ============================================

/**
 * Heroku 開発環境デプロイタスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 */
export default function (gulp) {
  gulp.task('deploy:dev:login', (done) => {
    try {
      console.log('=== Heroku login 確認 ===');
      ensureHerokuLogin();
      dockerLoginToHerokuRegistry();
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('deploy:dev:app:info', (done) => {
    try {
      heroku(`apps:info -a ${herokuAppName()}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('deploy:dev:app:create', (done) => {
    try {
      heroku(`create ${herokuAppName()} --stack container`);
      done();
    } catch (error) {
      done(error);
    }
  });

  SERVICES.forEach((service) => {
    gulp.task(`deploy:dev:build:${service.name}`, (done) => {
      try {
        console.log(`=== ${service.label} build ===`);
        buildImage(service);
        done();
      } catch (error) {
        done(error);
      }
    });

    gulp.task(`deploy:dev:push:${service.name}`, (done) => {
      try {
        console.log(`=== ${service.label} push ===`);
        pushImage(service);
        done();
      } catch (error) {
        done(error);
      }
    });

    gulp.task(`deploy:dev:release:${service.name}`, (done) => {
      try {
        console.log(`=== ${service.label} release ===`);
        releaseImage(service);
        done();
      } catch (error) {
        done(error);
      }
    });
  });

  gulp.task(
    'deploy:dev:build',
    gulp.series(...SERVICES.map((service) => `deploy:dev:build:${service.name}`)),
  );

  gulp.task(
    'deploy:dev:push',
    gulp.series(
      'deploy:dev:login',
      (done) => {
        try {
          ensureHerokuAppExists();
          done();
        } catch (error) {
          done(new Error(
            `Heroku アプリ ${herokuAppName()} が見つかりません。` +
            ` npx gulp deploy:dev:app:create を実行するか、DEV_HEROKU_APP_NAME を確認してください。`
          ));
        }
      },
      ...SERVICES.map((service) => `deploy:dev:push:${service.name}`)
    ),
  );

  gulp.task(
    'deploy:dev:release',
    gulp.series(...SERVICES.map((service) => `deploy:dev:release:${service.name}`)),
  );

  gulp.task(
    'deploy:dev',
    gulp.series('deploy:dev:build', 'deploy:dev:push', 'deploy:dev:release'),
  );

  gulp.task('deploy:dev:open', (done) => {
    try {
      heroku(`open -a ${herokuAppName()}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('deploy:dev:logs', (done) => {
    try {
      heroku(`logs --tail -a ${herokuAppName()}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('deploy:dev:status', (done) => {
    try {
      heroku(`ps -a ${herokuAppName()}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('deploy:dev:config', (done) => {
    try {
      heroku(`config -a ${herokuAppName()}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task(
    'deploy:dev:setup',
    gulp.series('deploy:dev:login', 'deploy:dev', 'deploy:dev:open'),
  );

  gulp.task('deploy:dev:help', (done) => {
    console.log(`
=== Heroku 開発環境デプロイコマンド ===

  必須環境変数:
    DEV_HEROKU_APP_NAME        Heroku アプリ名
    DEV_DOCKER_PLATFORM        Docker build platform（省略時: linux/amd64）

  認証:
    deploy:dev:login           Heroku ログイン確認 + Container Registry ログイン

  ビルド:
    deploy:dev:build           Docker イメージをローカル build（事前確認）

  push / release:
    deploy:dev:push            ローカル build 済みイメージを Registry に push
    deploy:dev:release         Heroku web プロセスを release
    deploy:dev                 build -> push -> release
    deploy:dev:setup           login -> build -> push -> release -> open

  運用:
    deploy:dev:open            Heroku アプリをブラウザで開く
    deploy:dev:logs            Heroku ログを tail
    deploy:dev:status          dyno 状態を表示
    deploy:dev:config          config vars を表示
    deploy:dev:app:info        Heroku アプリ情報を表示
    deploy:dev:app:create      Heroku アプリを作成（container stack）
    deploy:dev:help            このヘルプを表示
    `);
    done();
  });
}
