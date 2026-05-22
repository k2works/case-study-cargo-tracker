'use strict';

import { execSync } from 'child_process';
import { readFileSync, writeFileSync, existsSync } from 'fs';
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
  { name: `${PREFIX}-authms`,    service: 'authms',    port: 8081, type: 'backend' },
  { name: `${PREFIX}-routingms`, service: 'routingms', port: 8083, type: 'backend' },
  { name: `${PREFIX}-gatewayms`, service: 'gatewayms', port: 8080, type: 'backend' },
  { name: `${PREFIX}-frontend`,  service: 'frontend',  port: 80,   type: 'frontend' },
];

/** デプロイ順（依存関係を考慮） */
const DEPLOY_ORDER = ['authms', 'routingms', 'gatewayms', 'frontend'];

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
 * PEM ファイルを読み込み、改行を \n リテラルに変換して1行の文字列にする
 * @param {string} filePath
 * @returns {string}
 */
function readPem(filePath) {
  return readFileSync(filePath, 'utf8').trim().replace(/\r?\n/g, '\\n');
}

/**
 * .env ファイルの指定キーを upsert する
 * @param {string} key
 * @param {string} value
 */
function setEnvVar(key, value) {
  let content = existsSync(ENV_FILE) ? readFileSync(ENV_FILE, 'utf8') : '';
  const escaped = value.replace(/"/g, '\\"');
  const line = `${key}="${escaped}"`;
  const regex = new RegExp(`^${key}=.*$`, 'm');
  if (regex.test(content)) {
    content = content.replace(regex, line);
  } else {
    content = content.endsWith('\n') || content === ''
      ? content + line + '\n'
      : content + '\n' + line + '\n';
  }
  writeFileSync(ENV_FILE, content, 'utf8');
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
   heroku create ${PREFIX}-frontend  --stack container

   既存アプリの場合はスタックを container に設定:
   heroku stack:set container --app ${PREFIX}-authms
   heroku stack:set container --app ${PREFIX}-routingms
   heroku stack:set container --app ${PREFIX}-gatewayms
   heroku stack:set container --app ${PREFIX}-frontend

3. JWT_SECRET と Kafka 設定を .env に追加:
   echo 'JWT_SECRET="'$(openssl rand -base64 48)'"' >> .env
   # Aiven ダッシュボード > Connection Information から取得
   echo 'KAFKA_BOOTSTRAP_SERVERS="<host>:<port>"' >> .env
   echo 'KAFKA_SECURITY_PROTOCOL="SSL"' >> .env
   # Aiven から ca.pem / service.cert / service.key をダウンロードして
   # 証明書ディレクトリに置き、以下のコマンドで .env に登録:
   npx gulp deploy:dev:kafka:certs --certs /path/to/aiven-certs

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
  // Kafka 証明書登録
  // ──────────────────────────────────────────

  /**
   * Aiven Kafka の証明書ファイル（ca.pem / service.cert / service.key）を
   * .env に登録する。
   *
   * 使い方:
   *   npx gulp deploy:dev:kafka:certs --certs /path/to/aiven-certs
   *
   * ディレクトリ内のファイル名:
   *   ca.pem        → KAFKA_SSL_CA_CERT
   *   service.cert  → KAFKA_SSL_ACCESS_CERT
   *   service.key   → KAFKA_SSL_ACCESS_KEY
   */
  gulp.task('deploy:dev:kafka:certs', (done) => {
    // gulp の --certs オプションを取得（なければ環境変数 KAFKA_CERTS_DIR）
    const args = process.argv;
    const certsIdx = args.indexOf('--certs');
    const certsDir = certsIdx !== -1
      ? path.resolve(args[certsIdx + 1])
      : process.env.KAFKA_CERTS_DIR
        ? path.resolve(process.env.KAFKA_CERTS_DIR)
        : null;

    if (!certsDir) {
      console.error(
        '❌ 証明書ディレクトリを指定してください。\n' +
        '   npx gulp deploy:dev:kafka:certs --certs /path/to/aiven-certs\n' +
        '   または環境変数 KAFKA_CERTS_DIR=/path/to/aiven-certs を設定してください。'
      );
      process.exit(1);
    }

    const files = {
      KAFKA_SSL_CA_CERT:     path.join(certsDir, 'ca.pem'),
      KAFKA_SSL_ACCESS_CERT: path.join(certsDir, 'service.cert'),
      KAFKA_SSL_ACCESS_KEY:  path.join(certsDir, 'service.key'),
    };

    for (const [key, filePath] of Object.entries(files)) {
      if (!existsSync(filePath)) {
        console.error(`❌ ファイルが見つかりません: ${filePath}`);
        process.exit(1);
      }
      const value = readPem(filePath);
      setEnvVar(key, value);
      console.log(`✅ ${key} を .env に登録しました（${filePath}）`);
    }

    console.log('\n.env への証明書登録が完了しました。次のコマンドで Heroku に反映できます:');
    console.log('  npx gulp deploy:dev:config');
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

    const kafkaBootstrap  = getEnvVar('KAFKA_BOOTSTRAP_SERVERS');
    const kafkaProtocol   = getEnvVar('KAFKA_SECURITY_PROTOCOL') || 'SSL';
    const kafkaCaCert     = getEnvVar('KAFKA_SSL_CA_CERT');
    const kafkaAccessCert = getEnvVar('KAFKA_SSL_ACCESS_CERT');
    const kafkaAccessKey  = getEnvVar('KAFKA_SSL_ACCESS_KEY');
    if (!kafkaBootstrap) {
      console.warn('⚠️  .env に KAFKA_BOOTSTRAP_SERVERS が設定されていません。Kafka 設定はスキップします。');
    }

    console.log('[deploy:dev:config] ドメインを取得中...');
    const authDomain     = getDomain(`${PREFIX}-authms`);
    const routingDomain  = getDomain(`${PREFIX}-routingms`);
    const gatewayDomain  = getDomain(`${PREFIX}-gatewayms`);
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

    // routingms（Kafka 設定を含む）
    let routingKafkaVars = '';
    if (kafkaBootstrap) {
      routingKafkaVars +=
        `KAFKA_BOOTSTRAP_SERVERS="${kafkaBootstrap}" ` +
        `KAFKA_SECURITY_PROTOCOL="${kafkaProtocol}" `;
      if (kafkaCaCert)     routingKafkaVars += `KAFKA_SSL_CA_CERT="${kafkaCaCert}" `;
      if (kafkaAccessCert) routingKafkaVars += `KAFKA_SSL_ACCESS_CERT="${kafkaAccessCert}" `;
      if (kafkaAccessKey)  routingKafkaVars += `KAFKA_SSL_ACCESS_KEY="${kafkaAccessKey}" `;
    }
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      routingKafkaVars +
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

    // frontend
    execSync(
      `heroku config:set ` +
      `GATEWAY_URL="https://${gatewayDomain}" ` +
      `GATEWAY_HOST="${gatewayDomain}" ` +
      `--app ${PREFIX}-frontend`,
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

  SERVICES.forEach(({ name, service, type }) => {
    /**
     * サービスのイメージをビルドして Container Registry に push
     */
    gulp.task(`deploy:dev:push:${service}`, (done) => {
      const image = `registry.heroku.com/${name}/web`;
      if (type === 'frontend') {
        execSync(
          `docker build --platform linux/amd64 --provenance=false -t ${image} ${FRONTEND_DIR}`,
          { stdio: 'inherit' }
        );
      } else {
        const dockerfile = path.join(BACKEND_DIR, service, 'Dockerfile');
        execSync(
          `docker build --platform linux/amd64 --provenance=false -t ${image} -f ${dockerfile} ${BACKEND_DIR}`,
          { stdio: 'inherit' }
        );
      }
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
      const domain = getDomain(`${PREFIX}-frontend`);
      openUrl(`https://${domain}`);
    } catch {
      execSync(`heroku open --app ${PREFIX}-frontend`, { stdio: 'inherit' });
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
  deploy:dev:kafka:certs       Aiven Kafka 証明書を .env に登録
                               例: npx gulp deploy:dev:kafka:certs --certs /path/to/certs
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
  deploy:dev:push:frontend     frontend をビルド・プッシュ
  deploy:dev:release:frontend  frontend をリリース

【ログ確認】
  deploy:dev:logs:authms       authms のログを表示
  deploy:dev:logs:routingms    routingms のログを表示
  deploy:dev:logs:gatewayms    gatewayms のログを表示
  deploy:dev:logs:frontend     frontend のログを表示

【ブラウザ】
  deploy:dev:open              frontend をブラウザで開く

  deploy:dev:help              このヘルプを表示
    `);
    done();
  });
}
