'use strict';

/**
 * アプリケーション開発タスク (Haskell 版 Cargo Tracker)
 *
 * apps/cargo-tracker/ 配下の Haskell プロジェクトに対する
 * 開発タスク (DB 起動/停止、ビルド、テスト、Lint、フォーマット、TDD ループ等) を提供する。
 */

import path from 'path';
import { execSync, spawn } from 'child_process';
import { cleanDockerEnv, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

const APP_DIR = path.resolve('apps/cargo-tracker');

/** サービス定義 (将来複数アプリ化に備えて配列保持) */
const SERVICES = [
  { name: 'cargo-tracker', dir: APP_DIR, label: 'Cargo Tracker (Haskell)' },
];

// ============================================
// ヘルパー関数
// ============================================

/**
 * 指定ディレクトリでコマンドを実行する
 * @param {string} cmd - 実行するコマンド
 * @param {string} cwd - 作業ディレクトリ
 */
function runIn(cmd, cwd) {
  execSync(cmd, { cwd, stdio: 'inherit', env: cleanDockerEnv() });
}

/**
 * Stack コマンドを apps/cargo-tracker/ で実行する
 * @param {string} subcommand - stack のサブコマンド以降
 */
function stack(subcommand) {
  runIn(`stack ${subcommand}`, APP_DIR);
}

/**
 * docker compose コマンドを apps/cargo-tracker/ で実行する
 * @param {string} subcommand - docker compose のサブコマンド以降
 */
function compose(subcommand) {
  runIn(`docker compose ${subcommand}`, APP_DIR);
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  // --- DB (PostgreSQL) ---

  gulp.task('dev:db:start', (done) => {
    compose('up -d postgres adminer mailhog');
    done();
  });

  gulp.task('dev:db:stop', (done) => {
    compose('stop');
    done();
  });

  gulp.task('dev:db:logs', (done) => {
    compose('logs -f postgres');
    done();
  });

  gulp.task('dev:db:clean', (done) => {
    compose('down -v');
    done();
  });

  // --- ビルド / 実行 ---

  gulp.task('dev:setup', (done) => {
    stack('setup');
    done();
  });

  gulp.task('dev:build', (done) => {
    stack('build');
    done();
  });

  gulp.task('dev:build:pedantic', (done) => {
    stack('build --pedantic');
    done();
  });

  // 非ブロッキング起動: Ctrl+C で SIGINT を子プロセスに伝播し終了させる
  gulp.task('dev:run', () => new Promise((resolve, reject) => {
    const child = spawn('stack', ['run', 'cargo-tracker-exe'], {
      cwd: APP_DIR,
      stdio: 'inherit',
      env: cleanDockerEnv(),
    });
    const forward = (sig) => () => child.kill(sig);
    process.on('SIGINT', forward('SIGINT'));
    process.on('SIGTERM', forward('SIGTERM'));
    child.on('exit', (code) => {
      if (code === 0 || code === null) resolve();
      else reject(new Error(`cargo-tracker-exe exited with code ${code}`));
    });
  }));

  // アプリ起動後にブラウザを開く (foreground 起動と組み合わせるために独立タスク化)
  gulp.task('dev:open', async () => {
    const timeoutMs = 60000;
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      try {
        execSync('curl -sf http://localhost:8080/health -o /dev/null', { stdio: 'ignore' });
        openUrl('http://localhost:8080/health');
        return;
      } catch {
        await new Promise((r) => setTimeout(r, 1000));
      }
    }
    console.log('Warning: app did not respond within 60s; skip auto-open');
  });

  gulp.task('dev:ghci', (done) => {
    stack('ghci');
    done();
  });

  // --- テスト ---

  gulp.task('dev:test', (done) => {
    stack('test');
    done();
  });

  gulp.task('dev:test:coverage', (done) => {
    stack('test --coverage');
    stack('hpc report --all');
    done();
  });

  // TDD ホットリロード (ghcid)
  gulp.task('tdd:cargo-tracker', (done) => {
    runIn('ghcid --command="stack ghci" --test=":main"', APP_DIR);
    done();
  });

  // --- 品質 (Lint / Format / arch-check) ---

  gulp.task('dev:lint', (done) => {
    runIn('hlint src/ test/ app/', APP_DIR);
    done();
  });

  gulp.task('dev:format', (done) => {
    runIn('fourmolu --mode inplace src/ test/ app/', APP_DIR);
    done();
  });

  gulp.task('dev:format:check', (done) => {
    runIn('fourmolu --mode check src/ test/ app/', APP_DIR);
    done();
  });

  // arch-check Phase 1 (シェルスクリプト実装、ADR 0002 の 3 ルールを検査)
  gulp.task('dev:arch-check', (done) => {
    runIn('./scripts/arch-check.sh src', APP_DIR);
    done();
  });

  // --- マイグレーション (dbmate) ---

  gulp.task('dev:db:migrate', (done) => {
    runIn('dbmate --migrations-dir db/migrations up', APP_DIR);
    done();
  });

  gulp.task('dev:db:rollback', (done) => {
    runIn('dbmate --migrations-dir db/migrations rollback', APP_DIR);
    done();
  });

  // --- クリーン ---

  gulp.task('dev:clean', (done) => {
    stack('clean');
    done();
  });

  // --- 一括タスク ---

  // 初回セットアップ: DB 起動 -> stack setup -> マイグレーション -> build
  gulp.task('dev:bootstrap', gulp.series(
    'dev:db:start',
    'dev:setup',
    'dev:db:migrate',
    'dev:build',
  ));

  // CI 相当のローカル検証: format check -> lint -> arch-check -> test
  gulp.task('dev:verify', gulp.series(
    'dev:format:check',
    'dev:lint',
    'dev:arch-check',
    'dev:test',
  ));

  // --- ヘルプ ---

  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発タスク (Haskell 版 Cargo Tracker) ===

  [DB]
  dev:db:start          PostgreSQL / Adminer / MailHog を起動
  dev:db:stop           Docker Compose 全サービス停止
  dev:db:logs           PostgreSQL のログを追尾
  dev:db:clean          ボリュームごと削除 (データ消去)
  dev:db:migrate        dbmate でマイグレーション適用
  dev:db:rollback       dbmate で 1 ステップロールバック

  [ビルド / 実行]
  dev:setup             GHC を Stack で取得
  dev:build             stack build
  dev:build:pedantic    -Werror 相当の厳格ビルド
  dev:run               cargo-tracker-exe を起動
  dev:ghci              GHCi REPL を起動

  [テスト]
  dev:test              stack test
  dev:test:coverage     カバレッジ計測付きテスト (hpc report)
  tdd:cargo-tracker     ghcid によるホットリロード TDD ループ

  [品質]
  dev:lint              HLint
  dev:format            fourmolu --mode inplace
  dev:format:check      fourmolu --mode check
  dev:arch-check        ADR 0002 アーキテクチャ規約チェック (Phase 1: HLint)

  [一括]
  dev:bootstrap         初回セットアップ (DB→GHC→migrate→build)
  dev:verify            CI 相当 (format:check → lint → arch-check → test)

  [その他]
  dev:clean             stack clean
  dev:help              このヘルプを表示
`);
    done();
  });

  // SERVICES 配列を参照するだけのプレースホルダ (将来複数アプリ追加時に動的展開)
  void SERVICES;
}
