'use strict';

import { execSync } from 'child_process';
import { cleanDockerEnv, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

const PREFIX = 'DEV';
const BACKEND_DIR = 'apps/backend';
const FRONTEND_DIR = 'apps/frontend';
const GRADLEW = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

/** バックエンドサービス定義 */
const BACKEND_SERVICES = [
    { name: 'gatewayms', port: 8080, label: 'API Gateway' },
    { name: 'authms', port: 8081, label: 'Auth Service' },
    { name: 'bookingms', port: 8082, label: 'Booking Service' },
    { name: 'routingms', port: 8083, label: 'Routing Service' },
    { name: 'trackingms', port: 8084, label: 'Tracking Service' },
    { name: 'handlingms', port: 8085, label: 'Handling Service' },
    { name: 'billingms', port: 8086, label: 'Billing Service' },
];

/** フロントエンドサービス定義 */
const FRONTEND_SERVICE = { name: 'frontend', port: 3000, label: 'Frontend (React)' };

/** 全サービス */
const ALL_SERVICES = [...BACKEND_SERVICES, FRONTEND_SERVICE];

/** RabbitMQ（CloudAMQP）を使用するサービス */
const MESSAGING_SERVICES = ['bookingms', 'trackingms', 'handlingms', 'billingms'];

/** CloudAMQP アドオンをプロビジョニングするプライマリアプリ */
const CLOUDAMQP_PRIMARY = 'bookingms';

// ============================================
// ヘルパー関数
// ============================================

/**
 * Heroku アプリ名プレフィックスを取得する
 * @returns {string} プレフィックス
 */
const appPrefix = () => process.env[`${PREFIX}_HEROKU_APP_PREFIX`] || '';

/**
 * Docker ビルドプラットフォームを取得する
 * @returns {string} Docker プラットフォーム
 */
const dockerPlatform = () => process.env[`${PREFIX}_DOCKER_PLATFORM`] || 'linux/amd64';

/**
 * サービスの Heroku アプリ名を取得する
 * Heroku アプリ名は最大 30 文字の制約がある
 * @param {string} serviceName - サービス名
 * @returns {string} Heroku アプリ名
 */
function appName(serviceName) {
    const prefix = appPrefix();
    if (!prefix) {
        throw new Error('DEV_HEROKU_APP_PREFIX を .env に設定してください');
    }
    const name = `${prefix}-${serviceName}`;
    if (name.length > 30) {
        throw new Error(
            `アプリ名 "${name}" が 30 文字を超えています (${name.length} 文字)。` +
            `DEV_HEROKU_APP_PREFIX を短くしてください（現在: "${prefix}"）`
        );
    }
    return name;
}

/**
 * サービスのソースディレクトリを取得する
 * @param {object} service - サービス定義
 * @returns {string} ディレクトリパス
 */
function serviceDir(service) {
    if (service.name === 'frontend') {
        return FRONTEND_DIR;
    }
    return `${BACKEND_DIR}/${service.name}`;
}

/**
 * Heroku CLI コマンドを実行する
 * @param {string} command - heroku コマンド
 * @param {object} [options] - execSync オプション
 */
function heroku(command, options = {}) {
    execSync(`heroku ${command}`, { stdio: 'inherit', env: cleanDockerEnv(), ...options });
}

/**
 * Heroku CLI コマンドの標準出力を文字列で取得する
 * @param {string} command - heroku コマンド
 * @param {object} [options] - execSync オプション
 * @returns {string} コマンド出力
 */
function herokuCapture(command, options = {}) {
    return execSync(`heroku ${command}`, {
        env: cleanDockerEnv(),
        ...options,
    }).toString().trim();
}

/**
 * Heroku アプリの公開 URL を取得する
 * @param {string} app - Heroku アプリ名
 * @returns {string} 末尾スラッシュなしの URL
 */
function appWebUrl(app) {
    const info = JSON.parse(herokuCapture(`apps:info -a ${app} --json`));
    return (info?.app?.web_url || '').replace(/\/$/, '');
}

/**
 * Docker コマンドを実行する
 * @param {string} command - docker コマンド
 * @param {object} [options] - execSync オプション
 */
function docker(command, options = {}) {
    execSync(`docker ${command}`, { stdio: 'inherit', env: cleanDockerEnv(), ...options });
}

/**
 * CloudAMQP URL（amqp://user:pass@host:port/vhost あるいは amqps:// 版）を
 * Spring Boot 互換の個別環境変数に分解する。
 * Heroku CloudAMQP は単一 URL を提供するが、application-product.yml は
 * RABBITMQ_HOST / RABBITMQ_PORT / RABBITMQ_USERNAME / RABBITMQ_PASSWORD / RABBITMQ_VIRTUAL_HOST /
 * RABBITMQ_SSL_ENABLED で接続情報を読むため、ここで分解して個別変数に橋渡しする。
 *
 * @param {string} amqpUrl - amqp:// または amqps:// 形式の URL
 * @returns {{host: string, port: string, username: string, password: string, vhost: string, sslEnabled: string}}
 */
function parseAmqpUrl(amqpUrl) {
    const url = new URL(amqpUrl);
    const sslEnabled = url.protocol === 'amqps:';
    const port = url.port || (sslEnabled ? '5671' : '5672');
    // pathname は '/vhost' 形式。先頭スラッシュを除去し、URL デコード（vhost は '/' 等が % エスケープされて来る）
    const vhost = decodeURIComponent(url.pathname.replace(/^\//, '') || '/');
    return {
        host: url.hostname,
        port,
        username: decodeURIComponent(url.username),
        password: decodeURIComponent(url.password),
        vhost,
        sslEnabled: sslEnabled ? 'true' : 'false',
    };
}

/**
 * Heroku アプリに紐づく CloudAMQP アドオン一覧を取得する
 * @param {string} app - Heroku アプリ名
 * @returns {Array<{name: string, plan?: {name?: string}}>} CloudAMQP アドオン一覧
 */
function getCloudAmqpAddons(app) {
    const output = herokuCapture(`addons -a ${app} --json`);
    const addons = JSON.parse(output);
    return addons.filter((addon) => {
        const serviceName = addon?.addon_service?.name;
        const addonName = addon?.name || '';
        return serviceName === 'cloudamqp' || addonName.startsWith('cloudamqp-');
    });
}

// ============================================
// Gulp タスク
// ============================================

/**
 * 開発環境デプロイ（Heroku Container Registry）用 Gulp タスクを登録する
 * @param {import('gulp')} gulp
 */
export default function deployDevTasks(gulp) {

    // --------------------------------------------------------
    // ログイン
    // --------------------------------------------------------

    gulp.task('deploy:dev:login', (done) => {
        console.log('Heroku ログインは手動実行が必要です。');
        console.log('ターミナルで以下を実行してください:');
        console.log('  ! heroku login');
        console.log('  ! heroku container:login');
        done();
    });

    gulp.task('deploy:dev:container:login', (done) => {
        console.log('Heroku Container Registry にログインします...');
        try {
            heroku('container:login');
            done();
        } catch (e) {
            done(e);
        }
    });

    // --------------------------------------------------------
    // アプリ作成
    // --------------------------------------------------------

    // 全アプリ一括作成
    gulp.task('deploy:dev:app:create', (done) => {
        const prefix = appPrefix();
        if (!prefix) {
            done(new Error('DEV_HEROKU_APP_PREFIX を .env に設定してください'));
            return;
        }
        for (const svc of ALL_SERVICES) {
            const name = appName(svc.name);
            console.log(`Heroku アプリ "${name}" を作成します (${svc.label})...`);
            try {
                heroku(`create ${name} --stack container`);
            } catch (e) {
                console.warn(`  アプリ "${name}" の作成をスキップしました: ${e.message}`);
            }
        }
        done();
    });

    // --------------------------------------------------------
    // Config Vars 設定
    // --------------------------------------------------------

    gulp.task('deploy:dev:config', (done) => {
        const gatewayApp = appName('gatewayms');
        const authApp = appName('authms');
        const bookingApp = appName('bookingms');
        const routingApp = appName('routingms');
        const trackingApp = appName('trackingms');
        const handlingApp = appName('handlingms');
        const billingApp = appName('billingms');

        const gatewayUrl = appWebUrl(gatewayApp);
        const authUrl = appWebUrl(authApp);
        const bookingUrl = appWebUrl(bookingApp);
        const routingUrl = appWebUrl(routingApp);
        const trackingUrl = appWebUrl(trackingApp);
        const handlingUrl = appWebUrl(handlingApp);
        const billingUrl = appWebUrl(billingApp);

        // バックエンドサービス共通設定
        // SPRING_PROFILES_ACTIVE=product を設定して application-product.yml を有効化する。
        // 未設定だと default プロファイル（H2 メモリ DB・localhost RabbitMQ 既定値）で起動して
        // Heroku 上の CloudAMQP に接続できない。
        //
        // JAVA_OPTS は Heroku Eco/Basic dyno（512MB）に収めるための設定:
        //   - MaxRAMPercentage=50.0: ヒープ上限を約 256MB に抑え、JVM 非ヒープ領域とのバランスを取る
        //   - ReservedCodeCacheSize=64m: コードキャッシュを 64MB に制限
        //   - MaxMetaspaceSize=128m: メタスペース上限を 128MB に制限
        //   - 文字エンコーディング・タイムゾーンの明示
        const javaOpts = [
            '-XX:MaxRAMPercentage=50.0',
            '-XX:ReservedCodeCacheSize=64m',
            '-XX:MaxMetaspaceSize=128m',
            '-Dfile.encoding=UTF-8',
            '-Duser.timezone=Asia/Tokyo',
        ].join(' ');
        for (const svc of BACKEND_SERVICES) {
            const name = appName(svc.name);
            console.log(`Config Vars を設定します (${name})...`);
            try {
                heroku(`config:set JAVA_OPTS="${javaOpts}" SPRING_PROFILES_ACTIVE=product -a ${name}`);
            } catch (e) {
                console.warn(`  ${name} の設定をスキップしました: ${e.message}`);
            }
        }

        // Gateway: 各マイクロサービスのルーティング先 URL を設定
        // NOTE: 現状 HandlingActivityController は trackingms に同居しているため、
        //       HANDLINGMS_URL は handlingms (スケルトン) ではなく trackingms に向ける。
        //       handlingms 独立後は HANDLINGMS_URL=${handlingUrl} に戻す。
        //       handlingUrl 変数は手順の対称性維持のために計算したまま残し、ログにのみ出す。
        console.log(`Gateway のルーティング先を設定します (${gatewayApp})...`);
        console.log(`  HANDLINGMS_URL を暫定的に trackingms (${trackingUrl}) に向けます（handlingms: ${handlingUrl} は未実装）。`);
        try {
            heroku(
                `config:set AUTHMS_URL=${authUrl} BOOKINGMS_URL=${bookingUrl} ROUTINGMS_URL=${routingUrl} ` +
                `TRACKINGMS_URL=${trackingUrl} HANDLINGMS_URL=${trackingUrl} BILLINGMS_URL=${billingUrl} -a ${gatewayApp}`,
            );
        } catch (e) {
            console.warn(`  ${gatewayApp} のルーティング設定をスキップしました: ${e.message}`);
        }

        // フロントエンド: API Gateway の URL を設定
        const frontendApp = appName('frontend');
        console.log(`Config Vars を設定します (${frontendApp})...`);
        try {
            heroku(`config:set API_GATEWAY_URL=${gatewayUrl} -a ${frontendApp}`);
        } catch (e) {
            console.warn(`  ${frontendApp} の設定をスキップしました: ${e.message}`);
        }

        done();
    });

    // --------------------------------------------------------
    // CloudAMQP（RabbitMQ）
    // --------------------------------------------------------

    // CloudAMQP アドオンをプライマリアプリに追加
    gulp.task('deploy:dev:amqp:add', (done) => {
        const primary = appName(CLOUDAMQP_PRIMARY);
        console.log(`CloudAMQP アドオンを追加します (${primary})...`);
        try {
            const existingAddons = getCloudAmqpAddons(primary);
            if (existingAddons.length > 0) {
                console.log(`CloudAMQP は既に追加済みです（${existingAddons.length} 件）。`);
                console.log(`  ${existingAddons.map((addon) => addon.name).join(', ')}`);
                done();
                return;
            }
            heroku(`addons:create cloudamqp -a ${primary}`);
            console.log('CloudAMQP アドオンを追加しました。');
        } catch (e) {
            console.warn(`  スキップしました（既に追加済みの可能性）: ${e.message}`);
        }
        done();
    });

    // CloudAMQP URL をプライマリから取得し、Spring Boot 互換の個別変数に分解して
    // 全メッセージングサービス（プライマリ含む）に設定する。
    // application-product.yml は RABBITMQ_HOST / PORT / USERNAME / PASSWORD / VIRTUAL_HOST を読むため、
    // 単一の CLOUDAMQP_URL のままでは spring.rabbitmq に渡らず localhost にフォールバックしてしまう。
    gulp.task('deploy:dev:amqp:share', (done) => {
        const primary = appName(CLOUDAMQP_PRIMARY);
        console.log(`CloudAMQP URL を ${primary} から取得します...`);

        let amqpUrl;
        try {
            amqpUrl = execSync(`heroku config:get CLOUDAMQP_URL -a ${primary}`, {
                env: cleanDockerEnv(),
            }).toString().trim();
        } catch (e) {
            done(new Error(`CLOUDAMQP_URL を取得できませんでした: ${e.message}`));
            return;
        }

        if (!amqpUrl) {
            done(new Error(`CLOUDAMQP_URL が空です。先に deploy:dev:amqp:add を実行してください。`));
            return;
        }

        let parsed;
        try {
            parsed = parseAmqpUrl(amqpUrl);
        } catch (e) {
            done(new Error(`CLOUDAMQP_URL のパースに失敗しました: ${e.message}`));
            return;
        }

        console.log(
            `CLOUDAMQP_URL を分解しました` +
            `（host=${parsed.host}, port=${parsed.port}, vhost=${parsed.vhost}, ssl=${parsed.sslEnabled}）。`,
        );
        console.log('全メッセージングサービスに個別環境変数として設定します...');

        for (const svcName of MESSAGING_SERVICES) {
            const name = appName(svcName);
            console.log(`  ${name} に RABBITMQ_* / CLOUDAMQP_URL を設定します...`);
            try {
                heroku(
                    `config:set ` +
                    `CLOUDAMQP_URL="${amqpUrl}" ` +
                    `RABBITMQ_HOST="${parsed.host}" ` +
                    `RABBITMQ_PORT="${parsed.port}" ` +
                    `RABBITMQ_USERNAME="${parsed.username}" ` +
                    `RABBITMQ_PASSWORD="${parsed.password}" ` +
                    `RABBITMQ_VIRTUAL_HOST="${parsed.vhost}" ` +
                    `RABBITMQ_SSL_ENABLED="${parsed.sslEnabled}" ` +
                    `-a ${name}`,
                );
            } catch (e) {
                console.warn(`    ${name} の設定をスキップしました: ${e.message}`);
            }
        }

        done();
    });

    // CloudAMQP セットアップ（アドオン追加 → URL 共有）
    gulp.task('deploy:dev:amqp:setup', gulp.series(
        'deploy:dev:amqp:add',
        'deploy:dev:amqp:share',
    ));

    // CloudAMQP の状態確認
    gulp.task('deploy:dev:amqp:info', (done) => {
        const primary = appName(CLOUDAMQP_PRIMARY);
        console.log(`CloudAMQP アドオン情報 (${primary}):`);
        try {
            const cloudAmqpAddons = getCloudAmqpAddons(primary);
            if (cloudAmqpAddons.length === 0) {
                console.log('  CloudAMQP アドオンは見つかりませんでした。');
            } else {
                if (cloudAmqpAddons.length > 1) {
                    console.log(`  複数の CloudAMQP アドオンが見つかりました（${cloudAmqpAddons.length} 件）。`);
                }
                for (const addon of cloudAmqpAddons) {
                    console.log(`\n  - ${addon.name}`);
                    heroku(`addons:info ${addon.name} -a ${primary}`);
                }
            }
        } catch (e) {
            console.warn(`  情報取得できませんでした: ${e.message}`);
        }

        console.log('\n各サービスの CLOUDAMQP_URL 設定状況:');
        for (const svcName of MESSAGING_SERVICES) {
            const name = appName(svcName);
            try {
                const url = execSync(`heroku config:get CLOUDAMQP_URL -a ${name}`, {
                    env: cleanDockerEnv(),
                }).toString().trim();
                console.log(`  ${name}: ${url ? '設定済み' : '未設定'}`);
            } catch (e) {
                console.warn(`  ${name}: 取得失敗`);
            }
        }

        done();
    });

    // --------------------------------------------------------
    // ビルド
    // --------------------------------------------------------

    // バックエンド JAR ビルド
    gulp.task('deploy:dev:build:backend', (done) => {
        console.log('バックエンド JAR をビルドします...');
        try {
            execSync(`${GRADLEW} bootJar`, {
                cwd: BACKEND_DIR,
                stdio: 'inherit',
            });
            done();
        } catch (e) {
            done(e);
        }
    });

    // フロントエンドビルド（npm run build）
    gulp.task('deploy:dev:build:frontend', (done) => {
        console.log('フロントエンドをビルドします...');
        try {
            execSync('npm run build', {
                cwd: FRONTEND_DIR,
                stdio: 'inherit',
            });
            done();
        } catch (e) {
            done(e);
        }
    });

    // 全ビルド
    gulp.task('deploy:dev:build', gulp.parallel(
        'deploy:dev:build:backend',
        'deploy:dev:build:frontend',
    ));

    // --------------------------------------------------------
    // Push（docker build + docker push）
    // --------------------------------------------------------

    // サービスごとの push タスクを動的生成
    for (const svc of ALL_SERVICES) {
        gulp.task(`deploy:dev:push:${svc.name}`, (done) => {
            const name = appName(svc.name);
            const platform = dockerPlatform();
            const dir = serviceDir(svc);
            const image = `registry.heroku.com/${name}/web`;
            console.log(`Heroku に push します: ${name} (${svc.label})...`);
            try {
                const env = { ...cleanDockerEnv(), DOCKER_DEFAULT_PLATFORM: platform };
                docker(
                    `buildx build --platform ${platform} --provenance=false --sbom=false --load -t ${image} .`,
                    { cwd: dir, env },
                );
                docker(`push --platform ${platform} ${image}`, { cwd: dir, env });
                done();
            } catch (e) {
                done(e);
            }
        });
    }

    // 全サービス push（順次実行）
    gulp.task('deploy:dev:push', gulp.series(
        ...ALL_SERVICES.map(svc => `deploy:dev:push:${svc.name}`),
    ));

    // --------------------------------------------------------
    // Release
    // --------------------------------------------------------

    // サービスごとの release タスクを動的生成
    for (const svc of ALL_SERVICES) {
        gulp.task(`deploy:dev:release:${svc.name}`, (done) => {
            const name = appName(svc.name);
            console.log(`Heroku にリリースします: ${name} (${svc.label})...`);
            try {
                heroku(`container:release web -a ${name}`);
                done();
            } catch (e) {
                done(e);
            }
        });
    }

    // 全サービス release
    gulp.task('deploy:dev:release', gulp.series(
        ...ALL_SERVICES.map(svc => `deploy:dev:release:${svc.name}`),
    ));

    // --------------------------------------------------------
    // ステータス・ログ・オープン
    // --------------------------------------------------------

    gulp.task('deploy:dev:status', (done) => {
        for (const svc of ALL_SERVICES) {
            const name = appName(svc.name);
            console.log(`\n=== ${svc.label} (${name}) ===`);
            try {
                heroku(`ps -a ${name}`);
            } catch (e) {
                console.warn(`  ${name}: ${e.message}`);
            }
        }
        done();
    });

    gulp.task('deploy:dev:open', (done) => {
        const frontendApp = appName('frontend');
        console.log(`フロントエンドを開きます (${frontendApp})...`);
        try {
            heroku(`open -a ${frontendApp}`);
        } catch (e) {
            openUrl(`https://${frontendApp}.herokuapp.com`);
        }
        done();
    });

    gulp.task('deploy:dev:logs', (done) => {
        console.log('サービスを選んでログを確認してください:');
        for (const svc of ALL_SERVICES) {
            const name = appName(svc.name);
            console.log(`  heroku logs --tail -a ${name}  # ${svc.label}`);
        }
        done();
    });

    // --------------------------------------------------------
    // 複合タスク
    // --------------------------------------------------------

    // 更新デプロイ（build → push → release）
    gulp.task('deploy:dev', gulp.series(
        'deploy:dev:build',
        'deploy:dev:push',
        'deploy:dev:release',
    ));

    // 初回セットアップ（container:login → app:create → config → amqp → build → push → release）
    gulp.task('deploy:dev:setup', gulp.series(
        'deploy:dev:container:login',
        'deploy:dev:app:create',
        'deploy:dev:config',
        'deploy:dev:amqp:setup',
        'deploy:dev:build',
        'deploy:dev:push',
        'deploy:dev:release',
    ));

    // --------------------------------------------------------
    // ヘルプ
    // --------------------------------------------------------

    gulp.task('deploy:dev:help', (done) => {
        console.log(`
=== 開発環境デプロイコマンド (Heroku Container Registry / マイクロサービス構成) ===

【前提】heroku login は自動化不可。初回のみ手動実行してください:
  ! heroku login
  ! heroku container:login

--- 全体操作 ---
  deploy:dev:app:create       全 Heroku アプリを一括作成（container stack）
  deploy:dev:config           全アプリの Config Vars を設定
  deploy:dev:build            全サービスをビルド（JAR + フロントエンド）
  deploy:dev:push             全サービスを Heroku に push
  deploy:dev:release          全サービスをリリース
  deploy:dev                  更新デプロイ（build → push → release）
  deploy:dev:setup            初回セットアップ（create → config → amqp → build → push → release）

--- CloudAMQP（RabbitMQ） ---
  deploy:dev:amqp:add         CloudAMQP アドオンを追加（${CLOUDAMQP_PRIMARY} に）
  deploy:dev:amqp:share       CLOUDAMQP_URL をメッセージングサービスに共有
  deploy:dev:amqp:setup       アドオン追加 → URL 共有（一括）
  deploy:dev:amqp:info        CloudAMQP の設定状況を確認

  対象サービス: ${MESSAGING_SERVICES.join(', ')}

--- 個別サービス ---
  deploy:dev:push:<service>   特定サービスを push
  deploy:dev:release:<service> 特定サービスをリリース

  サービス名: ${ALL_SERVICES.map(s => s.name).join(', ')}

--- 確認・管理 ---
  deploy:dev:status           全サービスのデプロイ状態を確認
  deploy:dev:open             フロントエンドをブラウザで開く
  deploy:dev:logs             ログ確認コマンドを表示
  deploy:dev:help             このヘルプを表示

必要な環境変数 (.env):
  DEV_HEROKU_APP_PREFIX     Heroku アプリ名プレフィックス（必須）
  DEV_DOCKER_PLATFORM       Docker プラットフォーム（省略時: linux/amd64）

使用例:
  初回: ! heroku login  →  npx gulp deploy:dev:setup
  更新: npx gulp deploy:dev
  個別: npx gulp deploy:dev:push:authms && npx gulp deploy:dev:release:authms
        `);
        done();
    });
}
