'use strict';

import { execSync } from 'child_process';
import path from 'path';
import { openUrl } from './shared.js';

const ROOT        = path.resolve(process.cwd());
const BACKEND_DIR = path.join(ROOT, 'apps', 'backend');
const FRONTEND_DIR = path.join(ROOT, 'apps', 'frontend');
const ENV_FILE    = path.join(ROOT, '.env');

const PREFIX = 'cargo-tracker-5';

const JAVA_TOOL_OPTIONS =
  '-XX:MaxRAMPercentage=50.0 -XX:ReservedCodeCacheSize=64m ' +
  '-XX:MaxMetaspaceSize=128m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Tokyo';

/** IT1 で稼働しているサービス定義 */
const SERVICES = [
  { name: `${PREFIX}-authms`,    service: 'authms',    port: 8081 },
  { name: `${PREFIX}-routingms`, service: 'routingms', port: 8083 },
  { name: `${PREFIX}-gatewayms`, service: 'gatewayms', port: 8080 },
];

/** デプロイ順（依存関係を考慮） */
const DEPLOY_ORDER = ['authms', 'routingms', 'gatewayms'];

// ============================================
// ヘルパー
// ============================================

/**
 * Heroku domains から herokuapp.com ドメインを取得する
 * @param {string} appName
 * @returns {string}
 */
function getDomain(appName) {
  const json = execSync(`heroku domains --app ${appName} --json`, { encoding: 'utf8' });
  const domains = JSON.parse(json);
  const found = domains.find(d => d.hostname && d.hostname.includes('herokuapp.com'));
  if (!found) throw new Error(`[${appName}] herokuapp.com ドメインが見つかりません`);
  return found.hostname;
}

/**
 * .env ファイルから変数を読み込む
 * @param {string} key
 * @returns {string}
 */
function getEnvVar(key) {
  try {
    const content = execSync(`grep -E "^${key}=" ${ENV_FILE}`, { encoding: 'utf8' }).trim();
    return content.replace(/^[^=]+=["']?/, '').replace(/["']?\s*$/, '');
  } catch {
    return '';
  }
}

/**
 * Container Registry に push してリリース
 * @param {string} appName
 * @param {string} service
 */
function pushAndRelease(appName, service) {
  const dockerfile = path.join(BACKEND_DIR, service, 'Dockerfile');
  const image = `registry.heroku.com/${appName}/web`;
  execSync(`docker build -t ${image} -f ${dockerfile} ${BACKEND_DIR}`, { stdio: 'inherit' });
  execSync(`docker push ${image}`, { stdio: 'inherit' });
  execSync(`heroku container:release web --app ${appName}`, { stdio: 'inherit' });
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {

  // ──────────────────────────────────────────
  // セットアップ
  // ──────────────────────────────────────────

  /**
   * 初回セットアップガイドを表示
   */
  gulp.task('deploy:dev:setup', (done) => {
    console.log(`
=== Heroku 開発環境 初回セットアップ手順 ===

1. Heroku ログイン（ブラウザ認証が必要）:
   heroku login
   heroku container:login

2. アプリ作成（既存の場合はスキップ）:
   heroku create ${PREFIX}-authms    --stack container
   heroku create ${PREFIX}-routingms --stack container
   heroku create ${PREFIX}-gatewayms --stack container

3. JWT_SECRET を .env に追加:
   echo 'JWT_SECRET="'$(openssl rand -base64 48)'"' >> .env

4. Config Vars を一括設定:
   npx gulp deploy:dev:config

5. 全サービスをデプロイ:
   npx gulp deploy:dev

6. アプリを開く:
   npx gulp deploy:dev:open
    `);
    done();
  });

  // ──────────────────────────────────────────
  // Config Vars 設定
  // ──────────────────────────────────────────

  /**
   * heroku domains から実ドメインを自動取得して Config Vars を一括設定
   */
  gulp.task('deploy:dev:config', (done) => {
    const jwtSecret = getEnvVar('JWT_SECRET');
    if (!jwtSecret) {
      console.error('❌ .env に JWT_SECRET が設定されていません。先に追加してください。');
      process.exit(1);
    }

    console.log('[deploy:dev:config] ドメインを取得中...');
    const authDomain    = getDomain(`${PREFIX}-authms`);
    const routingDomain = getDomain(`${PREFIX}-routingms`);
    const gatewayDomain = getDomain(`${PREFIX}-gatewayms`);
    console.log(`  authms:    ${authDomain}`);
    console.log(`  routingms: ${routingDomain}`);
    console.log(`  gatewayms: ${gatewayDomain}`);

    // authms
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      `JWT_SECRET="${jwtSecret}" ` +
      `--app ${PREFIX}-authms`,
      { stdio: 'inherit' }
    );

    // routingms
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      `--app ${PREFIX}-routingms`,
      { stdio: 'inherit' }
    );

    // gatewayms
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      `JWT_SECRET="${jwtSecret}" ` +
      `AUTHMS_URL="https://${authDomain}" ` +
      `ROUTINGMS_URL="https://${routingDomain}" ` +
      `--app ${PREFIX}-gatewayms`,
      { stdio: 'inherit' }
    );

    console.log('\n✅ Config Vars 設定完了');
    done();
  });

  // ──────────────────────────────────────────
  // ビルド
  // ──────────────────────────────────────────

  /**
   * バックエンド全サービスの bootJar を生成
   */
  gulp.task('deploy:dev:build:backend', (done) => {
    const targets = DEPLOY_ORDER.filter(s => s !== 'gatewayms')
      .concat('gatewayms')
      .map(s => `:${s}:bootJar`)
      .join(' ');
    execSync(`./gradlew ${targets} -x test`, { cwd: BACKEND_DIR, stdio: 'inherit' });
    done();
  });

  // ──────────────────────────────────────────
  // push / release（サービス個別）
  // ──────────────────────────────────────────

  SERVICES.forEach(({ name, service }) => {
    /**
     * サービスのイメージをビルドして Container Registry に push
     */
    gulp.task(`deploy:dev:push:${service}`, (done) => {
      const dockerfile = path.join(BACKEND_DIR, service, 'Dockerfile');
      const image = `registry.heroku.com/${name}/web`;
      execSync(
        `docker build -t ${image} -f ${dockerfile} ${BACKEND_DIR}`,
        { stdio: 'inherit' }
      );
      execSync(`docker push ${image}`, { stdio: 'inherit' });
      done();
    });

    /**
     * push 済みイメージをリリース（dyno に反映）
     */
    gulp.task(`deploy:dev:release:${service}`, (done) => {
      execSync(`heroku container:release web --app ${name}`, { stdio: 'inherit' });
      done();
    });

    /**
     * サービスのログを表示（直近 100 行）
     */
    gulp.task(`deploy:dev:logs:${service}`, (done) => {
      execSync(`heroku logs --num=100 --app ${name}`, { stdio: 'inherit' });
      done();
    });
  });

  // ──────────────────────────────────────────
  // 全サービス一括デプロイ
  // ──────────────────────────────────────────

  /**
   * 全サービスを順次デプロイ（authms → routingms → gatewayms）
   */
  gulp.task('deploy:dev', gulp.series(
    ...DEPLOY_ORDER.map(svc => `deploy:dev:push:${svc}`),
    ...DEPLOY_ORDER.map(svc => `deploy:dev:release:${svc}`)
  ));

  // ──────────────────────────────────────────
  // ブラウザで開く
  // ──────────────────────────────────────────

  /**
   * gatewayms（API Gateway）をデフォルトブラウザで開く
   */
  gulp.task('deploy:dev:open', (done) => {
    try {
      const domain = getDomain(`${PREFIX}-gatewayms`);
      openUrl(`https://${domain}`);
    } catch {
      execSync(`heroku open --app ${PREFIX}-gatewayms`, { stdio: 'inherit' });
    }
    done();
  });

  // ──────────────────────────────────────────
  // ヘルプ
  // ──────────────────────────────────────────

  /**
   * Heroku デプロイタスク一覧を表示
   */
  gulp.task('deploy:dev:help', (done) => {
    console.log(`
=== Heroku 開発環境デプロイタスク ===

【セットアップ】
  deploy:dev:setup             初回セットアップガイドを表示
  deploy:dev:config            Config Vars を一括設定（実ドメイン自動取得）
  deploy:dev:build:backend     バックエンド全サービスの bootJar を生成

【デプロイ（全体）】
  deploy:dev                   全サービスを順次デプロイ（push → release）

【デプロイ（個別）】
  deploy:dev:push:authms       authms をビルド・プッシュ
  deploy:dev:release:authms    authms をリリース
  deploy:dev:push:routingms    routingms をビルド・プッシュ
  deploy:dev:release:routingms routingms をリリース
  deploy:dev:push:gatewayms    gatewayms をビルド・プッシュ
  deploy:dev:release:gatewayms gatewayms をリリース

【ログ確認】
  deploy:dev:logs:authms       authms のログを表示
  deploy:dev:logs:routingms    routingms のログを表示
  deploy:dev:logs:gatewayms    gatewayms のログを表示

【ブラウザ】
  deploy:dev:open              gatewayms をブラウザで開く

  deploy:dev:help              このヘルプを表示
    `);
    done();
  });
}
