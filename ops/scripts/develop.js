'use strict';

import path from 'path';
import { execSync, spawnSync } from 'child_process';
import { cleanDockerEnv, isDockerAvailable } from './shared.js';

// ============================================
// 設定
// ============================================

/** アプリケーションルートディレクトリ */
const APP_DIR = path.join(process.cwd(), 'apps', 'cargo-tracker');

/** E2E テストディレクトリ */
const E2E_DIR = path.join(process.cwd(), 'apps', 'e2e');

/** PostgreSQL サービス名（docker-compose.yml に合わせる） */
const DB_SERVICE = 'postgres';

/** Nix の Java devShell 名（flake.nix に定義）  */
const NIX_JAVA_SHELL = 'java';

// ============================================
// ヘルパー関数
// ============================================

/**
 * Gradle コマンドを実行する
 * @param {string} args - Gradle タスクおよびオプション
 */
function gradle(args) {
  const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
  execSync(`${gradlew} ${args}`, { cwd: APP_DIR, stdio: 'inherit' });
}

/**
 * Docker Compose コマンドを実行する
 * @param {string} args - docker compose に渡す引数
 */
function dockerCompose(args) {
  execSync(`docker compose ${args}`, { stdio: 'inherit', env: cleanDockerEnv() });
}

/**
 * Docker が利用可能か確認し、不可なら警告を出して false を返す
 * @returns {boolean} Docker が利用可能なら true
 */
function requireDocker() {
  if (isDockerAvailable()) {
    return true;
  }
  console.warn('Warning: Docker is not running. Skipping this task.');
  console.warn('Please start Docker Desktop and try again.');
  return false;
}

/**
 * コマンドが PATH 上に存在するか確認する
 * @param {string} cmd - 確認するコマンド名
 * @returns {boolean} 存在すれば true
 */
function commandExists(cmd) {
  const check = process.platform === 'win32' ? `where ${cmd}` : `which ${cmd}`;
  try {
    execSync(check, { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

/**
 * Java（JDK）が利用可能か確認する
 * @returns {boolean} `java` および `javac` が見つかれば true
 */
function isJavaAvailable() {
  return commandExists('java') && commandExists('javac');
}

/**
 * Nix が利用可能か確認する
 * @returns {boolean} `nix` コマンドが見つかれば true
 */
function isNixAvailable() {
  return commandExists('nix');
}

/**
 * Nix devShell 経由で Gradle コマンドを実行する（Mac/Linux 専用）
 * `nix develop .#<shell> --command <gradlew> <args>` を実行する
 * @param {string} args - Gradle タスクおよびオプション
 */
function gradleViaNix(args) {
  const gradlew = './gradlew';
  const result = spawnSync(
    'nix',
    ['develop', `.#${NIX_JAVA_SHELL}`, '--command', gradlew, ...args.split(' ')],
    { cwd: APP_DIR, stdio: 'inherit' }
  );
  if (result.status !== 0) {
    throw new Error(`Nix + Gradle failed with exit code ${result.status}`);
  }
}

/**
 * プラットフォームに応じて適切な Gradle 実行関数を返す
 * - Windows: gradle()
 * - Mac/Linux + Java あり: gradle()
 * - Mac/Linux + Java なし + Nix あり: gradleViaNix()
 * - Mac/Linux + Java なし + Nix なし: エラー
 * @returns {{ run: (args: string) => void, usingNix: boolean }}
 */
function resolveGradleRunner() {
  if (process.platform === 'win32') {
    if (!isJavaAvailable()) {
      throw new Error(
        'Java (JDK) が見つかりません。\n' +
        'https://jdk.java.net/25/ からインストールしてください。'
      );
    }
    return { run: gradle, usingNix: false };
  }

  // Mac / Linux
  if (isJavaAvailable()) {
    return { run: gradle, usingNix: false };
  }

  if (isNixAvailable()) {
    console.log('Java SDK が見つかりません。Nix devShell (.#java) を使用します...');
    return { run: gradleViaNix, usingNix: true };
  }

  throw new Error(
    'Java (JDK) も Nix も見つかりません。\n' +
    '以下のいずれかをインストールしてください:\n' +
    '  - SDKMAN: curl -s "https://get.sdkman.io" | bash\n' +
    '  - Nix:    https://nixos.org/download/'
  );
}

// ============================================
// Gulp タスク
// ============================================

/**
 * アプリケーション開発タスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 */
export default function (gulp) {

  // --------------------------------------------
  // セットアップタスク
  // --------------------------------------------

  gulp.task('dev:setup', async (done) => {
    const isWin = process.platform === 'win32';
    const platform = isWin ? 'Windows' : process.platform === 'darwin' ? 'macOS' : 'Linux';
    console.log(`\n=== アプリケーション開発環境セットアップ（${platform}）===\n`);

    // ステップ 1: Node.js 依存パッケージ
    console.log('[1/3] Node.js 依存パッケージをインストールします...');
    try {
      execSync('npm install', { stdio: 'inherit' });
      console.log('      ✓ npm install 完了\n');
    } catch (error) {
      done(new Error(`npm install に失敗しました: ${error.message}`));
      return;
    }

    // ステップ 2: Java / Nix の確認とビルド
    console.log('[2/3] Java 環境を確認してビルドを実行します...');
    let runner;
    try {
      runner = resolveGradleRunner();
    } catch (error) {
      done(error);
      return;
    }

    if (runner.usingNix) {
      console.log('      Nix devShell (.#java) 経由でビルドを実行します...');
    } else {
      try {
        const javaVer = execSync('java -version 2>&1', { encoding: 'utf8' }).trim().split('\n')[0];
        console.log(`      Java: ${javaVer}`);
      } catch {
        // java -version がない環境では無視
      }
    }

    try {
      runner.run('build -x test');
      console.log('      ✓ ビルド完了\n');
    } catch (error) {
      done(new Error(`ビルドに失敗しました: ${error.message}`));
      return;
    }

    // ステップ 3: テスト実行
    console.log('[3/3] テストを実行します...');
    try {
      runner.run('test');
      console.log('      ✓ テスト完了\n');
    } catch (error) {
      done(new Error(`テストに失敗しました: ${error.message}`));
      return;
    }

    console.log('=== セットアップ完了 ===\n');
    console.log('次のコマンドで開発を開始できます:');
    console.log('  npx gulp dev:backend:start     # 開発サーバー起動（H2）');
    console.log('  npx gulp dev:backend:tdd       # TDD モード');
    console.log('  npx gulp dev:help              # コマンド一覧\n');
    done();
  });

  // --------------------------------------------
  // バックエンドタスク
  // --------------------------------------------

  gulp.task('dev:backend:start', (done) => {
    try {
      console.log('Starting cargo-tracker (default profile / H2)...');
      console.log('URL: http://localhost:8080');
      gradle('bootRun');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:backend:start:product', (done) => {
    try {
      console.log('Starting cargo-tracker (product profile / PostgreSQL)...');
      console.log('URL: http://localhost:8080');
      gradle('bootRun --args="--spring.profiles.active=product"');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:backend:tdd', (done) => {
    try {
      console.log('Starting TDD mode (./gradlew test --continuous)...');
      console.log('Press Ctrl+D to stop.');
      gradle('test --continuous');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:backend:build', (done) => {
    try {
      console.log('Building cargo-tracker...');
      gradle('build');
      console.log('Build completed.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:backend:check', (done) => {
    try {
      console.log('Running quality checks (Checkstyle, SpotBugs, tests)...');
      gradle('check');
      console.log('All checks passed.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:backend:clean', (done) => {
    try {
      console.log('Cleaning build artifacts...');
      gradle('clean');
      console.log('Clean completed.');
      done();
    } catch (error) {
      done(error);
    }
  });

  // --------------------------------------------
  // データベースタスク
  // --------------------------------------------

  gulp.task('dev:db:start', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log('Starting PostgreSQL container...');
      dockerCompose(`up -d ${DB_SERVICE}`);
      console.log('PostgreSQL is running on localhost:5432');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:db:stop', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log('Stopping PostgreSQL container...');
      dockerCompose(`stop ${DB_SERVICE}`);
      console.log('PostgreSQL stopped.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:db:logs', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      dockerCompose(`logs -f ${DB_SERVICE}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // --------------------------------------------
  // E2E テストタスク
  // --------------------------------------------

  gulp.task('dev:e2e:install', (done) => {
    try {
      console.log('Installing E2E test dependencies...');
      execSync('npm ci', { cwd: E2E_DIR, stdio: 'inherit' });
      console.log('Installing Playwright browsers (chromium)...');
      execSync('npx playwright install chromium', { cwd: E2E_DIR, stdio: 'inherit' });
      console.log('E2E dependencies installed.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:e2e:run', (done) => {
    try {
      console.log('Running E2E tests (headless)...');
      console.log('Make sure the app is running at http://localhost:8080');
      execSync('npx playwright test', { cwd: E2E_DIR, stdio: 'inherit' });
      console.log('E2E tests completed.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('dev:e2e:ui', (done) => {
    try {
      console.log('Starting Playwright UI mode...');
      console.log('Make sure the app is running at http://localhost:8080');
      console.log('Press Ctrl+C to stop.');
      execSync('npx playwright test --ui', { cwd: E2E_DIR, stdio: 'inherit' });
      done();
    } catch (error) {
      done(error);
    }
  });

  // --------------------------------------------
  // ヘルプ
  // --------------------------------------------

  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発コマンド ===

  セットアップ:
    dev:setup                   開発環境の初期セットアップ（Java/Nix 自動選択）

  バックエンド:
    dev:backend:start           Spring Boot 起動（default / H2）
    dev:backend:start:product   Spring Boot 起動（product / PostgreSQL）
    dev:backend:tdd             テスト自動再実行（--continuous）
    dev:backend:build           ビルド（./gradlew build）
    dev:backend:check           品質チェック（Checkstyle, SpotBugs, テスト）
    dev:backend:clean           ビルド成果物を削除

  データベース:
    dev:db:start                PostgreSQL コンテナ起動（ポート 5432）
    dev:db:stop                 PostgreSQL コンテナ停止
    dev:db:logs                 PostgreSQL ログを表示

  E2E テスト:
    dev:e2e:install             依存パッケージと Playwright ブラウザをインストール
    dev:e2e:run                 E2E テスト実行（headless）※アプリ起動が必要
    dev:e2e:ui                  Playwright UI モードで E2E テスト実行 ※アプリ起動が必要

  ヘルプ:
    dev:help                    このヘルプを表示
    `);
    done();
  });
}
