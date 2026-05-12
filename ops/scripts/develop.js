'use strict';

import { execSync, spawn } from 'child_process';
import { cleanDockerEnv, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

const BACKEND_DIR = 'apps/backend';
const FRONTEND_DIR = 'apps/frontend';
const APPS_DIR = 'apps';
const GRADLEW = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

/** バックエンドサービス定義 */
const SERVICES = [
    { name: 'gatewayms', port: 8080, label: 'API Gateway' },
    { name: 'authms', port: 8081, label: 'Auth Service' },
    { name: 'bookingms', port: 8082, label: 'Booking Service' },
    { name: 'routingms', port: 8083, label: 'Routing Service' },
    { name: 'trackingms', port: 8084, label: 'Tracking Service' },
    { name: 'handlingms', port: 8085, label: 'Handling Service' },
    { name: 'billingms', port: 8086, label: 'Billing Service' },
];

// ============================================
// ヘルパー関数
// ============================================

/**
 * Gradle コマンドを同期実行する
 * @param {string} task - Gradle タスク名
 * @param {string[]} args - 追加引数
 */
function runGradle(task, args = []) {
    const cmd = [GRADLEW, task, ...args].join(' ');
    execSync(cmd, {
        cwd: BACKEND_DIR,
        stdio: 'inherit',
    });
}

/**
 * Gradle コマンドを非同期で起動する（dev サーバー等）
 * @param {string[]} gradleArgs - Gradle 引数
 * @param {Function} done - Gulp done コールバック
 */
function spawnGradle(gradleArgs, done) {
    const child = spawn(GRADLEW, gradleArgs, {
        cwd: BACKEND_DIR,
        stdio: 'inherit',
        shell: true,
    });
    child.on('close', (code) => done(code ? new Error(`Exit code: ${code}`) : undefined));
}

/**
 * npm コマンドをフロントエンドディレクトリで実行する
 * @param {string[]} npmArgs - npm 引数
 * @param {Function} done - Gulp done コールバック
 */
function spawnNpm(npmArgs, done) {
    const child = spawn('npm', npmArgs, {
        cwd: FRONTEND_DIR,
        stdio: 'inherit',
        shell: true,
    });
    child.on('close', (code) => done(code ? new Error(`Exit code: ${code}`) : undefined));
}

/**
 * Docker Compose コマンドを実行する
 * @param {string[]} composeArgs - docker compose 引数
 */
function runDockerCompose(composeArgs) {
    const cmd = ['docker', 'compose', ...composeArgs].join(' ');
    execSync(cmd, {
        cwd: APPS_DIR,
        stdio: 'inherit',
        env: cleanDockerEnv(),
    });
}

// ============================================
// Gulp タスク
// ============================================

/**
 * アプリケーション開発用 Gulp タスクを登録
 * @param {import('gulp')} gulp
 */
export default function developTasks(gulp) {

    // ----------------------------------------
    // バックエンド: 開発サーバー
    // ----------------------------------------

    // 個別サービスタスクを動的に生成（起動・product・TDD・テスト・ビルド・チェック・クリーン）
    for (const svc of SERVICES) {
        gulp.task(`dev:backend:${svc.name}`, (done) => {
            console.log(`Starting ${svc.label} (${svc.name}, port ${svc.port})...`);
            spawnGradle([`:${svc.name}:bootRun`], done);
        });

        gulp.task(`dev:backend:product:${svc.name}`, (done) => {
            console.log(`Starting ${svc.label} (${svc.name}, product profile / PostgreSQL)...`);
            spawnGradle([`:${svc.name}:bootRun`, "--args='--spring.profiles.active=product'"], done);
        });

        gulp.task(`dev:backend:local-prod:${svc.name}`, (done) => {
            console.log(`Starting ${svc.label} (${svc.name}, local-prod profile / Local PostgreSQL:5442)...`);
            spawnGradle([`:${svc.name}:bootRun`, "--args='--spring.profiles.active=local-prod'"], done);
        });

        gulp.task(`dev:backend:tdd:${svc.name}`, (done) => {
            console.log(`TDD mode: ${svc.label} (${svc.name})...`);
            spawnGradle([`:${svc.name}:test`, '--continuous'], done);
        });

        gulp.task(`dev:backend:test:${svc.name}`, (done) => {
            try {
                runGradle(`:${svc.name}:test`, [`:${svc.name}:jacocoTestReport`]);
                done();
            } catch (e) {
                done(e);
            }
        });

        gulp.task(`dev:backend:build:${svc.name}`, (done) => {
            try {
                runGradle(`:${svc.name}:build`);
                done();
            } catch (e) {
                done(e);
            }
        });

        gulp.task(`dev:backend:check:${svc.name}`, (done) => {
            try {
                runGradle(`:${svc.name}:check`);
                done();
            } catch (e) {
                done(e);
            }
        });

        gulp.task(`dev:backend:clean:${svc.name}`, (done) => {
            try {
                runGradle(`:${svc.name}:clean`);
                done();
            } catch (e) {
                done(e);
            }
        });
    }

    // 全サービス集約タスク（ループ後に定義）
    gulp.task('dev:backend', gulp.parallel(...SERVICES.map(svc => `dev:backend:${svc.name}`)));
    gulp.task('dev:backend:product', gulp.parallel(...SERVICES.map(svc => `dev:backend:product:${svc.name}`)));
    gulp.task('dev:backend:local-prod', gulp.parallel(...SERVICES.map(svc => `dev:backend:local-prod:${svc.name}`)));

    // ----------------------------------------
    // バックエンド: テスト（全サービス）
    // ----------------------------------------

    gulp.task('dev:backend:tdd', (done) => {
        console.log('Starting TDD mode (continuous test / all services)...');
        spawnGradle(['test', '--continuous'], done);
    });

    gulp.task('dev:backend:test', (done) => {
        try {
            runGradle('test', ['jacocoTestReport']);
            done();
        } catch (e) {
            done(e);
        }
    });

    // ----------------------------------------
    // バックエンド: ビルド・品質チェック（全サービス）
    // ----------------------------------------

    gulp.task('dev:backend:build', (done) => {
        try {
            runGradle('build');
            done();
        } catch (e) {
            done(e);
        }
    });

    gulp.task('dev:backend:check', (done) => {
        try {
            runGradle('check');
            done();
        } catch (e) {
            done(e);
        }
    });

    gulp.task('dev:backend:clean', (done) => {
        try {
            runGradle('clean');
            done();
        } catch (e) {
            done(e);
        }
    });

    // ----------------------------------------
    // フロントエンド
    // ----------------------------------------

    gulp.task('dev:frontend', (done) => {
        console.log('Starting frontend dev server (port 3000)...');
        spawnNpm(['run', 'dev'], done);
    });

    gulp.task('dev:frontend:build', (done) => {
        try {
            execSync('npm run build', { cwd: FRONTEND_DIR, stdio: 'inherit' });
            done();
        } catch (e) {
            done(e);
        }
    });

    gulp.task('dev:frontend:test', (done) => {
        try {
            execSync('npm test', { cwd: FRONTEND_DIR, stdio: 'inherit' });
            done();
        } catch (e) {
            done(e);
        }
    });

    gulp.task('dev:frontend:tdd', (done) => {
        console.log('Starting frontend TDD mode (vitest watch)...');
        spawnNpm(['run', 'test:watch'], done);
    });

    gulp.task('dev:frontend:lint', (done) => {
        try {
            execSync('npm run lint', { cwd: FRONTEND_DIR, stdio: 'inherit' });
            done();
        } catch (e) {
            done(e);
        }
    });

    // ----------------------------------------
    // Docker Compose（DB・メッセージブローカー）
    // ----------------------------------------

    gulp.task('dev:db:start', (done) => {
        console.log('Starting PostgreSQL and RabbitMQ...');
        try {
            runDockerCompose(['up', '-d', 'postgres', 'rabbitmq']);
            done();
        } catch (e) {
            done(e);
        }
    });

    gulp.task('dev:db:stop', (done) => {
        console.log('Stopping containers...');
        try {
            runDockerCompose(['down']);
            done();
        } catch (e) {
            done(e);
        }
    });

    gulp.task('dev:db:status', (done) => {
        try {
            runDockerCompose(['ps']);
            done();
        } catch (e) {
            done(e);
        }
    });

    gulp.task('dev:db:logs', (done) => {
        try {
            runDockerCompose(['logs', '--tail=50']);
            done();
        } catch (e) {
            done(e);
        }
    });

    // ----------------------------------------
    // ブラウザで開く
    // ----------------------------------------

    gulp.task('dev:open', (done) => {
        openUrl('http://localhost:3000');
        done();
    });

    gulp.task('dev:open:swagger', (done) => {
        openUrl('http://localhost:8082/swagger-ui.html');
        done();
    });

    gulp.task('dev:open:rabbitmq', (done) => {
        openUrl('http://localhost:15672');
        done();
    });

    // ----------------------------------------
    // 複合タスク
    // ----------------------------------------

    // 旧タスク名の後方互換（gulpfile.js の export で使用）
    gulp.task('cargo-tracker:dev', gulp.series('dev:backend'));
    gulp.task('cargo-tracker:tdd', gulp.series('dev:backend:tdd'));
    gulp.task('cargo-tracker:test', gulp.series('dev:backend:test'));
    gulp.task('cargo-tracker:build', gulp.series('dev:backend:build'));
    gulp.task('cargo-tracker:check', gulp.series('dev:backend:check'));

    // ----------------------------------------
    // ヘルプ
    // ----------------------------------------

    gulp.task('dev:help', (done) => {
        console.log(`
╔══════════════════════════════════════════════════════════════╗
║                   開発タスク一覧                              ║
╚══════════════════════════════════════════════════════════════╝

  バックエンド（全サービス）:
    dev:backend                  全サービス起動（並列 / H2 メモリ DB）
    dev:backend:local-prod       全サービス起動（並列 / ローカル PostgreSQL:5442・本番擬似検証）
    dev:backend:product          全サービス起動（並列 / クラウド本番想定・環境変数で接続先注入）
    dev:backend:tdd              TDD モード（全サービス / テスト自動再実行）
    dev:backend:test             テスト実行 + カバレッジ（全サービス）
    dev:backend:build            ビルド（全サービス）
    dev:backend:check            品質チェック（全サービス）
    dev:backend:clean            ビルド成果物削除（全サービス）

  バックエンド（個別サービス）:
    dev:backend:{service}              個別サービス起動（H2 メモリ DB）
    dev:backend:local-prod:{service}   個別サービス起動（ローカル PostgreSQL:5442）
    dev:backend:product:{service}      個別サービス起動（クラウド本番想定）
    dev:backend:tdd:{service}          個別サービス TDD モード
    dev:backend:test:{service}         個別サービス テスト + カバレッジ
    dev:backend:build:{service}        個別サービス ビルド
    dev:backend:check:{service}        個別サービス 品質チェック
    dev:backend:clean:{service}        個別サービス クリーン
                                       {service}: gatewayms|authms|bookingms|routingms|
                                                  trackingms|handlingms|billingms

  フロントエンド:
    dev:frontend                 開発サーバー起動（port 3000）
    dev:frontend:build           本番ビルド
    dev:frontend:test            テスト実行
    dev:frontend:tdd             TDD モード（Vitest watch）
    dev:frontend:lint            ESLint 実行

  Docker Compose:
    dev:db:start                 PostgreSQL + RabbitMQ 起動
    dev:db:stop                  コンテナ停止
    dev:db:status                コンテナ状態確認
    dev:db:logs                  コンテナログ表示

  ブラウザ:
    dev:open                     フロントエンド（localhost:3000）
    dev:open:swagger             Swagger UI（localhost:8082）
    dev:open:rabbitmq            RabbitMQ 管理 UI（localhost:15672）

  ショートカット（npm スクリプト）:
    npm start                    → dev:backend
    npm run tdd                  → dev:backend:tdd
    npm test                     → dev:backend:test
    npm run build                → dev:backend:build
    npm run check                → dev:backend:check
`);
        done();
    });
}
