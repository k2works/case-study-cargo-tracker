'use strict';

import { execSync } from 'child_process';
import { cleanDockerEnv, isDockerAvailable } from './shared.js';

// ============================================
// 設定
// ============================================

/** アプリケーションディレクトリ（CargoTracker.sln） */
const APP_DIR = 'apps/cargo-tracker';

/** Web ホストプロジェクト */
const WEB_PROJECT = 'src/CargoTracker.Web';

/** サービス定義 */
const SERVICES = [
  { name: 'app', project: WEB_PROJECT, port: 8080, label: 'Cargo Tracker（国際貨物輸送管理）' },
];

// ============================================
// ヘルパー関数
// ============================================

/**
 * コマンドを実行（ログ付き・出力は端末へ）
 * @param {string} command - 実行するコマンド
 * @param {object} [options] - execSync に渡す追加オプション
 */
function run(command, options = {}) {
  console.log(`$ ${command}`);
  execSync(command, { stdio: 'inherit', cwd: APP_DIR, ...options });
}

/**
 * Docker Compose コマンドを実行（DOCKER_HOST を除外した環境で実行）
 * @param {string} args - docker compose に渡す引数
 */
function compose(args) {
  console.log(`$ docker compose ${args}`);
  execSync(`docker compose ${args}`, {
    stdio: 'inherit',
    cwd: APP_DIR,
    env: cleanDockerEnv(),
  });
}

/**
 * Docker が起動していることを確認し、未起動なら例外を投げる
 */
function requireDocker() {
  if (!isDockerAvailable()) {
    throw new Error('Docker が起動していません。Docker Desktop を起動してください');
  }
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  // 開発サーバー起動（Development プロファイル / SQLite・Docker 不要）
  SERVICES.forEach((svc) => {
    gulp.task(`dev:${svc.name}`, (done) => {
      try {
        console.log(`=== ${svc.label} 開発サーバー起動（Development / SQLite・live reload） ===`);
        // dotnet watch run: ソース（.cs / .cshtml / wwwroot）変更を検知して
        // ホットリロード + ブラウザ自動更新（live reload）する。
        run(`dotnet watch run --project ${svc.project}`);
        done();
      } catch (error) {
        done(error);
      }
    });

    // 本番互換起動（PostgreSQL / Docker 必要）
    gulp.task(`dev:${svc.name}:product`, (done) => {
      try {
        requireDocker();
        console.log(`=== ${svc.label} 本番互換起動（Staging / PostgreSQL） ===`);
        compose('up -d db --wait');
        run(`dotnet run --project ${svc.project}`, {
          env: { ...process.env, ASPNETCORE_ENVIRONMENT: 'Staging' },
        });
        done();
      } catch (error) {
        done(error);
      }
    });

    // TDD モード（テスト自動再実行）
    gulp.task(`tdd:${svc.name}`, (done) => {
      try {
        console.log(`=== ${svc.label} TDD モード（dotnet watch test） ===`);
        run('dotnet watch test --project tests/CargoTracker.Domain.Tests');
        done();
      } catch (error) {
        done(error);
      }
    });
  });

  // DB コンテナの起動 / 停止 / ログ
  gulp.task('dev:db:start', (done) => {
    try {
      requireDocker();
      compose('up -d db --wait');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:db:stop', (done) => {
    try {
      requireDocker();
      compose('down');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:db:logs', (done) => {
    try {
      requireDocker();
      compose('logs -f db');
      done();
    } catch (error) {
      done(error);
    }
  });

  // 全テスト実行（カバレッジ付き・E2E は除外）
  gulp.task('dev:test', (done) => {
    try {
      run('dotnet test --filter "Category!=E2E" --collect:"XPlat Code Coverage"');
      done();
    } catch (error) {
      done(error);
    }
  });

  // E2E テスト（Playwright・Chromium ヘッドレス + 実プロセス起動）
  gulp.task('dev:e2e', (done) => {
    try {
      console.log('=== Playwright E2E テスト（ログイン → 荷主登録 / 見積作成） ===');
      // E2E フィクスチャは dotnet run --no-build で Web を起動するため、先にビルドする。
      run('dotnet build CargoTracker.sln --nologo');
      // Chromium を導入（導入済みならスキップされる）。pwsh が必要。
      run('pwsh tests/CargoTracker.E2E.Tests/bin/Debug/net10.0/playwright.ps1 install chromium');
      run('dotnet test tests/CargoTracker.E2E.Tests/CargoTracker.E2E.Tests.csproj --no-build --nologo');
      done();
    } catch (error) {
      done(error);
    }
  });

  // 品質チェック（フォーマット検証 + Analyzers + 全テスト）
  gulp.task('dev:check', (done) => {
    try {
      run('dotnet format --verify-no-changes');
      run('dotnet build -warnaserror');
      run('dotnet test');
      done();
    } catch (error) {
      done(error);
    }
  });

  // ヘルプ
  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発タスク ===

  dev:app              開発サーバー起動（Development / SQLite・Docker 不要・live reload）
  dev:app:product      本番互換起動（Staging / PostgreSQL・Docker 必要）
  tdd:app              TDD モード（dotnet watch test）
  dev:db:start         PostgreSQL コンテナ起動（healthy まで待機）
  dev:db:stop          コンテナ停止・削除
  dev:db:logs          DB ログのリアルタイム表示
  dev:test             全テスト実行（カバレッジ付き・E2E 除外）
  dev:e2e              Playwright E2E テスト（Chromium ヘッドレス・要 pwsh）
  dev:check            品質チェック（format → build -warnaserror → test）
  dev:help             このヘルプを表示

詳細: docs/operation/アプリケーション開発環境セットアップ手順書.md
`);
    done();
  });
}
