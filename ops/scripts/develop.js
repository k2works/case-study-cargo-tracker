'use strict';

import path from 'path';
import { execSync } from 'child_process';
import { cleanDockerEnv } from './shared.js';

// ============================================
// 設定
// ============================================

/** サービス定義 */
const SERVICES = [
  {
    name: 'cargo-tracker',
    dir: 'apps/cargo-tracker',
    port: 8080,
    dbPort: 5432,
    dbService: 'postgres',
    serverCrate: 'cargo-tracker-server',
    migrations: 'crates/infra-persistence/migrations',
    dbUrl: 'postgres://cargo:cargo@localhost:5432/cargo_tracker',
    label: '貨物輸送管理',
  },
];

// ============================================
// ヘルパー関数
// ============================================

/**
 * サービスのワークスペースディレクトリの絶対パスを返す
 * @param {object} svc - サービス定義
 * @returns {string} 絶対パス
 */
function serviceDir(svc) {
  return path.resolve(process.cwd(), svc.dir);
}

/**
 * サービスのワークスペース内でコマンドを実行する
 * @param {object} svc - サービス定義
 * @param {string} command - 実行するコマンド
 * @param {object} [extraEnv] - 追加の環境変数（既存の process.env を上書き）
 */
function runInService(svc, command, extraEnv = {}) {
  try {
    execSync(command, {
      cwd: serviceDir(svc),
      stdio: 'inherit',
      env: { ...cleanDockerEnv(), ...extraEnv },
    });
  } catch (err) {
    console.error(`エラー: ${command} が失敗しました (${err.message})`);
    process.exit(1);
  }
}

/**
 * sqlx CLI 等が必要とする DATABASE_URL を解決する（環境変数優先、無ければ既定値）。
 * @param {object} svc - サービス定義
 * @returns {object} DATABASE_URL を含む環境変数オブジェクト
 */
function dbEnv(svc) {
  return { DATABASE_URL: process.env.DATABASE_URL || svc.dbUrl };
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  SERVICES.forEach((svc) => {
    // 開発サーバー起動
    gulp.task(`dev:${svc.name}`, (done) => {
      runInService(svc, `cargo run -p ${svc.serverCrate}`);
      done();
    });

    // TDD モード（テスト自動再実行）
    gulp.task(`tdd:${svc.name}`, (done) => {
      runInService(svc, 'cargo watch -x "test --workspace"');
      done();
    });

    // ライブリロード開発（コード/テンプレート変更で自動リビルド・再起動 + ブラウザ再読込）
    gulp.task(`dev:${svc.name}:watch`, (done) => {
      runInService(
        svc,
        `cargo watch -w crates -x "run -p ${svc.serverCrate} --bin ${svc.serverCrate}"`,
        { ...dbEnv(svc), LIVERELOAD: '1' }
      );
      done();
    });

    // ビルド
    gulp.task(`dev:${svc.name}:build`, (done) => {
      runInService(svc, 'cargo build --workspace');
      done();
    });

    // テスト
    gulp.task(`dev:${svc.name}:test`, (done) => {
      runInService(svc, 'cargo test --workspace');
      done();
    });

    // カバレッジ（HTML レポート）
    gulp.task(`dev:${svc.name}:coverage`, (done) => {
      runInService(svc, 'cargo llvm-cov --workspace --html');
      console.log(`レポート: ${svc.dir}/target/llvm-cov/html/index.html`);
      done();
    });

    // 品質チェック（fmt / clippy / audit / deny）
    gulp.task(`dev:${svc.name}:quality`, (done) => {
      runInService(svc, 'cargo fmt --all -- --check');
      runInService(svc, 'cargo clippy --workspace --all-targets -- -D warnings');
      runInService(svc, 'cargo audit');
      runInService(svc, 'cargo deny check');
      done();
    });

    // フルチェック（品質チェック + 全テスト）
    gulp.task(
      `dev:${svc.name}:check`,
      gulp.series(`dev:${svc.name}:quality`, `dev:${svc.name}:test`)
    );

    // DB 起動
    gulp.task(`dev:${svc.name}:db:start`, (done) => {
      runInService(svc, `docker compose up -d ${svc.dbService}`);
      done();
    });

    // DB 停止
    gulp.task(`dev:${svc.name}:db:stop`, (done) => {
      runInService(svc, 'docker compose down');
      done();
    });

    // DB ログ
    gulp.task(`dev:${svc.name}:db:logs`, (done) => {
      runInService(svc, `docker compose logs -f ${svc.dbService}`);
      done();
    });

    // マイグレーション適用（埋め込みマイグレータ・sqlx-cli 非依存）
    gulp.task(`dev:${svc.name}:db:migrate`, (done) => {
      runInService(svc, `cargo run -p ${svc.serverCrate} --bin migrate`, dbEnv(svc));
      done();
    });

    // sqlx オフラインキャッシュ生成
    gulp.task(`dev:${svc.name}:db:prepare`, (done) => {
      runInService(svc, 'cargo sqlx prepare --workspace', dbEnv(svc));
      done();
    });

    // 開発用ユーザーの seed（ログイン検証用・冪等）
    gulp.task(`dev:${svc.name}:db:seed`, (done) => {
      runInService(svc, `cargo run -p ${svc.serverCrate} --bin seed`, dbEnv(svc));
      done();
    });
  });

  // ヘルプタスク（必須）
  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発コマンド ===

  dev:cargo-tracker             開発サーバー起動 (http://localhost:8080)
  dev:cargo-tracker:watch       ライブリロード開発（自動リビルド・再起動 + ブラウザ再読込）
  tdd:cargo-tracker             TDD モード（テスト自動再実行）
  dev:cargo-tracker:build       ワークスペース全体をビルド
  dev:cargo-tracker:test        全テスト実行（統合テストは Docker 必須）
  dev:cargo-tracker:coverage    カバレッジ HTML レポート生成
  dev:cargo-tracker:quality     品質チェック (fmt / clippy / audit / deny)
  dev:cargo-tracker:check       品質チェック + 全テスト
  dev:cargo-tracker:db:start    PostgreSQL コンテナ起動
  dev:cargo-tracker:db:stop     PostgreSQL コンテナ停止
  dev:cargo-tracker:db:logs     PostgreSQL ログ表示
  dev:cargo-tracker:db:migrate  マイグレーション適用
  dev:cargo-tracker:db:seed     開発用ユーザー投入（ログイン検証用・冪等）
  dev:cargo-tracker:db:prepare  sqlx オフラインキャッシュ (.sqlx) 生成
  dev:help                      このヘルプを表示
    `);
    done();
  });
}
