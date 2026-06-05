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

/** 稼働中のサービス定義（IT2 で bookingms 追加、IT5 で trackingms / handlingms 追加、IT7 で billingms 追加） */
const SERVICES = [
  { name: `${PREFIX}-authms`,     service: 'authms',     port: 8081, type: 'backend' },
  { name: `${PREFIX}-bookingms`,  service: 'bookingms',  port: 8082, type: 'backend' },
  { name: `${PREFIX}-routingms`,  service: 'routingms',  port: 8083, type: 'backend' },
  { name: `${PREFIX}-trackingms`, service: 'trackingms', port: 8084, type: 'backend' },
  { name: `${PREFIX}-handlingms`, service: 'handlingms', port: 8085, type: 'backend' },
  { name: `${PREFIX}-billingms`,  service: 'billingms',  port: 8086, type: 'backend' },
  { name: `${PREFIX}-gatewayms`,  service: 'gatewayms',  port: 8080, type: 'backend' },
  { name: `${PREFIX}-frontend`,   service: 'frontend',   port: 80,   type: 'frontend' },
];

/** デプロイ順（依存関係を考慮: 業務サービス先行 → trackingms / handlingms → billingms → gatewayms → frontend） */
const DEPLOY_ORDER = ['authms', 'bookingms', 'routingms', 'trackingms', 'handlingms', 'billingms', 'gatewayms', 'frontend'];

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
 * .env ファイルから変数を読み込む（Windows / Mac / Linux 共通）。
 * grep に依存せず Node.js の fs API で読み込むことでクロスプラットフォーム対応。
 * @param {string} key
 * @returns {string}
 */
function getEnvVar(key) {
  if (!existsSync(ENV_FILE)) return '';
  const content = readFileSync(ENV_FILE, 'utf8');
  // 行頭から `<KEY>=` で始まり、値部分を取り出す。行末改行 / クォート / コメント混入に対応。
  const lineRegex = new RegExp(`^${key}=(.*)$`, 'm');
  const match = content.match(lineRegex);
  if (!match) return '';
  // 値の前後のクォートと末尾空白を除去
  return match[1].trim().replace(/^["']/, '').replace(/["']$/, '');
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
   heroku create ${PREFIX}-authms     --stack container
   heroku create ${PREFIX}-bookingms  --stack container   # IT2 追加
   heroku create ${PREFIX}-routingms  --stack container
   heroku create ${PREFIX}-trackingms --stack container   # IT5 追加
   heroku create ${PREFIX}-handlingms --stack container   # IT5 追加
   heroku create ${PREFIX}-billingms  --stack container   # IT7 追加
   heroku create ${PREFIX}-gatewayms  --stack container
   heroku create ${PREFIX}-frontend   --stack container

   既存アプリの場合はスタックを container に設定:
   heroku stack:set container --app ${PREFIX}-authms
   heroku stack:set container --app ${PREFIX}-bookingms
   heroku stack:set container --app ${PREFIX}-routingms
   heroku stack:set container --app ${PREFIX}-trackingms
   heroku stack:set container --app ${PREFIX}-handlingms
   heroku stack:set container --app ${PREFIX}-billingms
   heroku stack:set container --app ${PREFIX}-gatewayms
   heroku stack:set container --app ${PREFIX}-frontend

3. PostgreSQL アドオン（Read Model 用、各 backend サービス毎）:
   heroku addons:create heroku-postgresql:essential-0 --app ${PREFIX}-bookingms
   heroku addons:create heroku-postgresql:essential-0 --app ${PREFIX}-routingms
   heroku addons:create heroku-postgresql:essential-0 --app ${PREFIX}-trackingms   # IT5 追加（tracking_summary / tracking_event / tracking_exception）
   heroku addons:create heroku-postgresql:essential-0 --app ${PREFIX}-handlingms   # IT5 追加（cargo_snapshot / handling_activity）
   heroku addons:create heroku-postgresql:essential-0 --app ${PREFIX}-billingms    # IT7 追加（invoice / invoice_line / payment）
   # authms は Spring Security JWT のみで DB 不要だが、ユーザーマスタを使う場合は同様に追加

3.1. SendGrid アドオン（IT8 T3.3 / ADR-0018、Dynamic Templates によるメール通知）:
   heroku addons:create sendgrid:starter --app ${PREFIX}-trackingms  # 40,000 通/月、$15/月
   heroku addons:create sendgrid:starter --app ${PREFIX}-billingms

   # 各 app の SENDGRID_API_KEY / SENDGRID_USERNAME / SENDGRID_PASSWORD は Add-on 作成時に
   # 自動で Config Vars にセットされる。Heroku → Add-on → SendGrid の Dashboard を開き、
   # Sender Authentication（Single Sender or Domain Authentication）を完了させること。
   # その後、Dynamic Templates 画面で 9 種類のテンプレートを作成し、各 d-xxxxx ID をメモする：
   #   trackingms: trackingIssued / statusChanged / misrouted / exceptionRegistered
   #               / exceptionResolved / exceptionEscalation
   #   billingms : invoiceIssued / paymentReceived / overdue
   # テンプレート ID は手順 5（npx gulp deploy:dev:config）で
   # SENDGRID_TEMPLATE_* 環境変数として一括投入する。

4. JWT_SECRET と Kafka 設定を .env に追加:
   echo 'JWT_SECRET="'$(openssl rand -base64 48)'"' >> .env
   # IT6 追加: 公開追跡照会の時限署名トークン（trackingms 専用、authms と別鍵）
   echo 'TRACKING_PUBLIC_TOKEN_SECRET="'$(openssl rand -base64 48)'"' >> .env
   # Aiven ダッシュボード > Connection Information から取得
   echo 'KAFKA_BOOTSTRAP_SERVERS="<host>:<port>"' >> .env
   echo 'KAFKA_SECURITY_PROTOCOL="SSL"' >> .env
   # Aiven から ca.pem / service.cert / service.key をダウンロードして
   # 証明書ディレクトリに置き、以下のコマンドで .env に登録:
   npx gulp deploy:dev:kafka:certs --certs /path/to/aiven-certs

5. Config Vars を一括設定:
   npx gulp deploy:dev:config

6. 全サービスをデプロイ（DEPLOY_ORDER: authms → bookingms → routingms → trackingms → handlingms → billingms → gatewayms → frontend）:
   npx gulp deploy:dev

7. アプリを開く:
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

    // IT6 追加: 公開追跡照会の時限署名トークン用秘密鍵（authms と別鍵、ADR-0013）
    const trackingPublicTokenSecret = getEnvVar('TRACKING_PUBLIC_TOKEN_SECRET');
    if (!trackingPublicTokenSecret) {
      console.warn('⚠️  .env に TRACKING_PUBLIC_TOKEN_SECRET が設定されていません。trackingms の公開照会機能（US18）が dev デフォルト鍵で動作します。');
      console.warn('    本番設定: echo \'TRACKING_PUBLIC_TOKEN_SECRET="\'$(openssl rand -base64 48)\'"\' >> .env');
    }

    // IT8 追加: SendGrid Dynamic Templates の通知設定（ADR-0018、T3.1/T3.2）
    // SENDGRID_API_KEY は Add-on（heroku addons:create sendgrid:starter）で自動投入されるため
    // ここでは notification.adapter=sendgrid とテンプレート ID のみ Config Vars に追加する。
    // Heroku SendGrid Dashboard → Dynamic Templates 画面で作成した d-xxxxx ID を .env に記載：
    //   SENDGRID_TEMPLATE_TRACKING_ISSUED / STATUS_CHANGED / MISROUTED
    //   SENDGRID_TEMPLATE_EXCEPTION_REGISTERED / RESOLVED / ESCALATION
    //   SENDGRID_TEMPLATE_INVOICE_ISSUED / PAYMENT_RECEIVED / OVERDUE
    const notificationAdapter = getEnvVar('NOTIFICATION_ADAPTER') || 'logging';
    const sendgridFromEmail = getEnvVar('SENDGRID_FROM_EMAIL') || 'noreply@cargo-tracker.example.com';
    const sendgridFromName = getEnvVar('SENDGRID_FROM_NAME') || 'Cargo Tracker';
    const trackingTemplates = {
      TRACKING_ISSUED:       getEnvVar('SENDGRID_TEMPLATE_TRACKING_ISSUED'),
      STATUS_CHANGED:        getEnvVar('SENDGRID_TEMPLATE_STATUS_CHANGED'),
      MISROUTED:             getEnvVar('SENDGRID_TEMPLATE_MISROUTED'),
      EXCEPTION_REGISTERED:  getEnvVar('SENDGRID_TEMPLATE_EXCEPTION_REGISTERED'),
      EXCEPTION_RESOLVED:    getEnvVar('SENDGRID_TEMPLATE_EXCEPTION_RESOLVED'),
      EXCEPTION_ESCALATION:  getEnvVar('SENDGRID_TEMPLATE_EXCEPTION_ESCALATION'),
    };
    const billingTemplates = {
      INVOICE_ISSUED:    getEnvVar('SENDGRID_TEMPLATE_INVOICE_ISSUED'),
      PAYMENT_RECEIVED:  getEnvVar('SENDGRID_TEMPLATE_PAYMENT_RECEIVED'),
      OVERDUE:           getEnvVar('SENDGRID_TEMPLATE_OVERDUE'),
    };
    if (notificationAdapter === 'sendgrid') {
      const missingTracking = Object.entries(trackingTemplates).filter(([, v]) => !v).map(([k]) => k);
      const missingBilling = Object.entries(billingTemplates).filter(([, v]) => !v).map(([k]) => k);
      if (missingTracking.length > 0 || missingBilling.length > 0) {
        console.warn('⚠️  NOTIFICATION_ADAPTER=sendgrid だが SendGrid テンプレート ID が未設定:');
        if (missingTracking.length > 0) console.warn(`    trackingms 未設定: ${missingTracking.join(', ')}`);
        if (missingBilling.length > 0)  console.warn(`    billingms 未設定:  ${missingBilling.join(', ')}`);
        console.warn('    Heroku SendGrid Dashboard → Dynamic Templates で d-xxxxx ID を発行して .env に記載してください。');
      }
    }
    const buildNotificationVars = (templates) => {
      let vars =
        `NOTIFICATION_ADAPTER="${notificationAdapter}" ` +
        `SENDGRID_FROM_EMAIL="${sendgridFromEmail}" ` +
        `SENDGRID_FROM_NAME="${sendgridFromName}" `;
      for (const [key, value] of Object.entries(templates)) {
        if (value) vars += `SENDGRID_TEMPLATE_${key}="${value}" `;
      }
      return vars;
    };
    const trackingNotificationVars = buildNotificationVars(trackingTemplates);
    const billingNotificationVars = buildNotificationVars(billingTemplates);

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
    const bookingDomain  = getDomain(`${PREFIX}-bookingms`);
    const routingDomain  = getDomain(`${PREFIX}-routingms`);
    const trackingDomain = getDomain(`${PREFIX}-trackingms`);   // IT5 追加
    const handlingDomain = getDomain(`${PREFIX}-handlingms`);   // IT5 追加
    const billingDomain  = getDomain(`${PREFIX}-billingms`);    // IT7 追加
    const gatewayDomain  = getDomain(`${PREFIX}-gatewayms`);
    console.log(`  authms:     ${authDomain}`);
    console.log(`  bookingms:  ${bookingDomain}`);
    console.log(`  routingms:  ${routingDomain}`);
    console.log(`  trackingms: ${trackingDomain}`);
    console.log(`  handlingms: ${handlingDomain}`);
    console.log(`  billingms:  ${billingDomain}`);
    console.log(`  gatewayms:  ${gatewayDomain}`);

    // 業務サービスに共通の Kafka 設定（KAFKA_BOOTSTRAP_SERVERS 指定時のみ付与）
    let kafkaVars = '';
    if (kafkaBootstrap) {
      kafkaVars +=
        `KAFKA_BOOTSTRAP_SERVERS="${kafkaBootstrap}" ` +
        `KAFKA_SECURITY_PROTOCOL="${kafkaProtocol}" `;
      if (kafkaCaCert)     kafkaVars += `KAFKA_SSL_CA_CERT="${kafkaCaCert}" `;
      if (kafkaAccessCert) kafkaVars += `KAFKA_SSL_ACCESS_CERT="${kafkaAccessCert}" `;
      if (kafkaAccessKey)  kafkaVars += `KAFKA_SSL_ACCESS_KEY="${kafkaAccessKey}" `;
    }

    // authms
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      `JWT_SECRET="${jwtSecret}" ` +
      `--app ${PREFIX}-authms`,
      { stdio: 'inherit' }
    );

    // bookingms（Kafka 設定を含む）
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      kafkaVars +
      `--app ${PREFIX}-bookingms`,
      { stdio: 'inherit' }
    );

    // routingms（Kafka 設定を含む）
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      kafkaVars +
      `--app ${PREFIX}-routingms`,
      { stdio: 'inherit' }
    );

    // trackingms（IT5 追加、Kafka + 公開追跡照会トークン）
    // TRACKING_PUBLIC_TOKEN_SECRET は IT6 / ADR-0013 で追加、authms と別鍵
    let trackingExtraVars = '';
    if (trackingPublicTokenSecret) {
      trackingExtraVars = `TRACKING_PUBLIC_TOKEN_SECRET="${trackingPublicTokenSecret}" `;
    }
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      kafkaVars +
      trackingExtraVars +
      trackingNotificationVars +
      `--app ${PREFIX}-trackingms`,
      { stdio: 'inherit' }
    );

    // handlingms（IT5 追加、Kafka）
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      kafkaVars +
      `--app ${PREFIX}-handlingms`,
      { stdio: 'inherit' }
    );

    // billingms（IT7 追加、Kafka + bookingms 同期参照 ADR-0015）
    // BOOKINGMS_URL は RestShipperInfoAcl が GET /api/v1/shippers/{id} で参照
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      kafkaVars +
      `BOOKINGMS_URL="https://${bookingDomain}" ` +
      billingNotificationVars +
      `--app ${PREFIX}-billingms`,
      { stdio: 'inherit' }
    );

    // gatewayms（IT5 で trackingms / handlingms ルート、IT7 で billingms ルート追加）
    execSync(
      `heroku config:set ` +
      `SPRING_PROFILES_ACTIVE=heroku ` +
      `"JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}" ` +
      `JWT_SECRET="${jwtSecret}" ` +
      `AUTHMS_URL="https://${authDomain}" ` +
      `BOOKINGMS_URL="https://${bookingDomain}" ` +
      `ROUTINGMS_URL="https://${routingDomain}" ` +
      `TRACKINGMS_URL="https://${trackingDomain}" ` +
      `HANDLINGMS_URL="https://${handlingDomain}" ` +
      `BILLINGMS_URL="https://${billingDomain}" ` +
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
   * バックエンド全サービスの bootJar を生成（gatewayms は最後）
   */
  gulp.task('deploy:dev:build:backend', (done) => {
    const backendSvcs = SERVICES
      .filter(svc => svc.type === 'backend')
      .map(svc => svc.service);
    const targets = backendSvcs.filter(s => s !== 'gatewayms')
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
   * 全サービスを順次デプロイ（authms → bookingms → routingms → gatewayms → frontend）
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

【デプロイ（個別）】  ※実行順は DEPLOY_ORDER に準拠
  deploy:dev:push:authms       authms をビルド・プッシュ
  deploy:dev:release:authms    authms をリリース
  deploy:dev:push:bookingms    bookingms をビルド・プッシュ（IT2 追加）
  deploy:dev:release:bookingms bookingms をリリース
  deploy:dev:push:routingms    routingms をビルド・プッシュ
  deploy:dev:release:routingms routingms をリリース
  deploy:dev:push:trackingms   trackingms をビルド・プッシュ（IT5 追加）
  deploy:dev:release:trackingms trackingms をリリース
  deploy:dev:push:handlingms   handlingms をビルド・プッシュ（IT5 追加）
  deploy:dev:release:handlingms handlingms をリリース
  deploy:dev:push:billingms    billingms をビルド・プッシュ（IT7 追加）
  deploy:dev:release:billingms billingms をリリース
  deploy:dev:push:gatewayms    gatewayms をビルド・プッシュ
  deploy:dev:release:gatewayms gatewayms をリリース
  deploy:dev:push:frontend     frontend をビルド・プッシュ
  deploy:dev:release:frontend  frontend をリリース

【ログ確認】
  deploy:dev:logs:authms       authms のログを表示
  deploy:dev:logs:bookingms    bookingms のログを表示（IT2 追加）
  deploy:dev:logs:routingms    routingms のログを表示
  deploy:dev:logs:trackingms   trackingms のログを表示（IT5 追加）
  deploy:dev:logs:handlingms   handlingms のログを表示（IT5 追加）
  deploy:dev:logs:billingms    billingms のログを表示（IT7 追加）
  deploy:dev:logs:gatewayms    gatewayms のログを表示
  deploy:dev:logs:frontend     frontend のログを表示

【ブラウザ】
  deploy:dev:open              frontend をブラウザで開く

  deploy:dev:help              このヘルプを表示
    `);
    done();
  });
}
