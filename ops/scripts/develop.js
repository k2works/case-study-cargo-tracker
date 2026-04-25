'use strict';

import { execSync, spawn } from 'child_process';
import { cleanDockerEnv, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

const BACKEND_DIR = 'apps/backend';
const FRONTEND_DIR = 'apps/frontend';
const APPS_DIR = 'apps';
const GRADLEW = './gradlew';

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

    gulp.task('dev:backend', (done) => {
        console.log('Starting bookingms (default profile / H2)...');
        spawnGradle([':bookingms:bootRun'], done);
    });

    gulp.task('dev:backend:product', (done) => {
        console.log('Starting bookingms (product profile / PostgreSQL)...');
        spawnGradle([':bookingms:bootRun', "--args='--spring.profiles.active=product'"], done);
    });

    // 個別サービス起動タスクを動的に生成
    for (const svc of SERVICES) {
        gulp.task(`dev:backend:${svc.name}`, (done) => {
            console.log(`Starting ${svc.label} (${svc.name}, port ${svc.port})...`);
            spawnGradle([`:${svc.name}:bootRun`], done);
        });
    }

    // ----------------------------------------
    // バックエンド: テスト
    // ----------------------------------------

    gulp.task('dev:backend:tdd', (done) => {
        console.log('Starting TDD mode (continuous test)...');
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
    // バックエンド: ビルド・品質チェック
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

  バックエンド:
    dev:backend                  開発サーバー起動（bookingms / H2）
    dev:backend:product          開発サーバー起動（bookingms / PostgreSQL）
    dev:backend:{service}        個別サービス起動
                                 (gatewayms|authms|bookingms|routingms|
                                  trackingms|handlingms|billingms)
    dev:backend:tdd              TDD モード（テスト自動再実行）
    dev:backend:test             テスト実行 + カバレッジ
    dev:backend:build            ビルド
    dev:backend:check            品質チェック（Checkstyle + SpotBugs + テスト）
    dev:backend:clean            ビルド成果物削除

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
