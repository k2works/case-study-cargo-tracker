'use strict';

/**
 * アプリケーション開発タスク（Flix）
 *
 * Flix はパッケージマネージャを持たず、単一の実行可能 JAR（flix.jar）として配布される。
 * 本スクリプトは flix.jar の取得からビルド・テスト・実行までを統合する。
 *
 * 詳細は docs/operation/アプリケーション開発環境セットアップ手順書.md を参照。
 */

import path from 'path';
import fs from 'fs';
import https from 'https';
import { execSync } from 'child_process';
import { openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

/** Flix コンパイラのバージョン（flix.toml の flix フィールドと一致させること） */
const FLIX_VERSION = process.env.FLIX_VERSION || '0.75.1';

/** flix.jar の配置先（Git 管理外） */
const FLIX_JAR = path.join(process.cwd(), 'ops', 'tools', 'flix', 'flix.jar');

/** Flix リリースの取得元 */
const FLIX_DOWNLOAD_URL = `https://github.com/flix/flix/releases/download/v${FLIX_VERSION}/flix.jar`;

/** アプリケーション定義 */
const APPS = [
  {
    name: 'cargo-tracker',
    dir: path.join(process.cwd(), 'apps', 'cargo-tracker'),
    label: '国際貨物輸送管理システム',
    port: 8080,
  },
];

/** 既定のアプリケーション（単一アプリ構成のため先頭を使う） */
const DEFAULT_APP = APPS[0];

/** TDD モードで監視するパターン */
const WATCH_GLOBS = ['apps/*/src/**/*.flix', 'apps/*/test/**/*.flix'];

// ============================================
// ヘルパー関数
// ============================================

/**
 * flix.jar が存在するか確認する
 * @returns {boolean} 存在すれば true
 */
function isFlixInstalled() {
  return fs.existsSync(FLIX_JAR);
}

/**
 * flix.jar の存在を確認し、なければ導入手順を案内して終了する
 * @returns {void}
 */
function requireFlix() {
  if (isFlixInstalled()) return;
  console.error(`
Flix コンパイラが見つかりません: ${FLIX_JAR}

以下のコマンドで取得してください。

  npx gulp dev:setup
`);
  process.exit(1);
}

/**
 * URL からファイルをダウンロードする（リダイレクト追従）
 * @param {string} url - ダウンロード元 URL
 * @param {string} dest - 保存先パス
 * @returns {Promise<void>}
 */
function download(url, dest) {
  return new Promise((resolve, reject) => {
    https
      .get(url, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          download(res.headers.location, dest).then(resolve, reject);
          return;
        }
        if (res.statusCode !== 200) {
          res.resume();
          reject(new Error(`ダウンロードに失敗しました (HTTP ${res.statusCode}): ${url}`));
          return;
        }
        const file = fs.createWriteStream(dest);
        res.pipe(file);
        file.on('finish', () => file.close(() => resolve()));
        file.on('error', reject);
      })
      .on('error', reject);
  });
}

/**
 * Flix CLI コマンドを実行する
 * @param {string} subcommand - Flix のサブコマンド（build / test / run 等）
 * @param {object} [options] - オプション
 * @param {object} [options.app] - 対象アプリケーション定義（既定は DEFAULT_APP）
 * @param {string} [options.args] - サブコマンドに続けて渡す引数
 * @param {boolean} [options.ignoreError] - エラー時に終了しない
 * @returns {boolean} 成功したら true
 */
function flix(subcommand, options = {}) {
  requireFlix();
  const app = options.app || DEFAULT_APP;
  const args = options.args ? ` ${options.args}` : '';
  const cmd = `java -jar "${FLIX_JAR}" ${subcommand}${args}`;
  try {
    // 開発向けの挙動（サンプルデータ投入・ログイン画面の既定値）は APP_ENV で明示する。
    // 「環境変数が無ければ開発」という判定は、本番で注入に失敗したときに
    // 開発用の資格情報が表に出るフェイルオープンになる
    execSync(cmd, { cwd: app.dir, stdio: 'inherit', env: { ...process.env, APP_ENV: 'development' } });
    return true;
  } catch (err) {
    if (options.ignoreError) {
      console.error(`\n${subcommand} が失敗しました。修正して再実行してください。`);
      return false;
    }
    console.error(`エラー: ${err.message}`);
    process.exit(1);
    // `process.exit` で到達しないが、**返り値の有無を経路ごとに揃える**。
    // 揃っていないと、呼び出し側が `undefined` を偽と読んで
    // 「失敗したのに成功扱い」の逆の取り違えが起きうる
    return false;
  }
}

/**
 * fat JAR を実行する
 *
 * build-jar は Maven 依存を同梱しないため、実行には build-fatjar を用いる。
 * @param {object} [app] - 対象アプリケーション定義
 * @returns {void}
 */
function runFatJar(app = DEFAULT_APP) {
  const jar = path.join(app.dir, 'artifact', `${app.name}.jar`);
  if (!fs.existsSync(jar)) {
    console.error(`
実行可能 JAR が見つかりません: ${jar}

先に以下を実行してください。

  npx gulp dev:jar
`);
    process.exit(1);
  }
  try {
    execSync(`java -jar "${jar}"`, { cwd: app.dir, stdio: 'inherit' });
  } catch (err) {
    console.error(`エラー: ${err.message}`);
    process.exit(1);
  }
}

/**
 * ビルド生成物を削除する
 * @param {object} [app] - 対象アプリケーション定義
 * @returns {void}
 */
function cleanArtifacts(app = DEFAULT_APP) {
  ['build', 'artifact'].forEach((dir) => {
    const target = path.join(app.dir, dir);
    if (fs.existsSync(target)) {
      fs.rmSync(target, { recursive: true, force: true });
      console.log(`削除しました: ${path.relative(process.cwd(), target)}`);
    }
  });
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  // --- セットアップ ---

  gulp.task('dev:setup', async () => {
    if (isFlixInstalled()) {
      console.log('Flix は導入済みです。バージョンを確認します。');
      execSync(`java -jar "${FLIX_JAR}" --version`, { stdio: 'inherit' });
      return;
    }
    fs.mkdirSync(path.dirname(FLIX_JAR), { recursive: true });
    console.log(`Flix ${FLIX_VERSION} を取得します...`);
    await download(FLIX_DOWNLOAD_URL, FLIX_JAR);
    console.log(`取得しました: ${path.relative(process.cwd(), FLIX_JAR)}`);
    execSync(`java -jar "${FLIX_JAR}" --version`, { stdio: 'inherit' });
  });

  gulp.task('dev:version', (done) => {
    requireFlix();
    execSync(`java -jar "${FLIX_JAR}" --version`, { stdio: 'inherit' });
    done();
  });

  // --- ビルド・テスト ---

  gulp.task('dev:check', (done) => {
    flix('check');
    done();
  });

  gulp.task('dev:build', (done) => {
    flix('build');
    done();
  });

  gulp.task('dev:test', (done) => {
    flix('test');
    done();
  });

  gulp.task('dev:format', (done) => {
    flix('format');
    done();
  });

  gulp.task('dev:doc', (done) => {
    flix('doc');
    done();
  });

  gulp.task('dev:outdated', (done) => {
    flix('outdated');
    done();
  });

  // --- TDD モード ---

  // `done` を受け取らない。watch を継続するため呼ばないのが正しく、
  // 受け取ると「呼び忘れ」と見分けが付かない
  gulp.task('dev:tdd', () => {
    requireFlix();
    console.log(`
=== TDD モード ===

  ソースとテストの変更を監視し、保存のたびにテストを実行します。
  終了するには Ctrl+C を押してください。
`);
    // 初回実行
    flix('test', { ignoreError: true });
    gulp.watch(WATCH_GLOBS, (cb) => {
      console.log('\n--- 変更を検出しました。テストを実行します ---');
      flix('test', { ignoreError: true });
      cb();
    });
  });

  // --- 実行 ---

  gulp.task('dev:run', (done) => {
    flix('run');
    done();
  });

  gulp.task('dev:jar', (done) => {
    // build-jar は Maven 依存を同梱しないため build-fatjar を使う
    flix('build-fatjar');
    const jar = path.join(DEFAULT_APP.dir, 'artifact', `${DEFAULT_APP.name}.jar`);
    console.log(`\n生成しました: ${path.relative(process.cwd(), jar)}`);
    done();
  });

  gulp.task('dev:jar:run', (done) => {
    runFatJar();
    done();
  });

  gulp.task('dev:repl', (done) => {
    flix('repl');
    done();
  });

  gulp.task('dev:open', (done) => {
    openUrl(`http://localhost:${DEFAULT_APP.port}`);
    done();
  });

  // --- クリーンアップ ---

  gulp.task('dev:clean', (done) => {
    cleanArtifacts();
    done();
  });

  // --- 複合タスク ---

  /** コミット前の品質チェック（ビルド + テスト） */
  gulp.task('dev:verify', gulp.series('dev:build', 'dev:test'));

  // --- ヘルプ ---

  gulp.task('dev:help', (done) => {
    console.log(`
=== アプリケーション開発コマンド（Flix ${FLIX_VERSION}）===

  セットアップ
    dev:setup        Flix コンパイラ（flix.jar）を取得する
    dev:version      Flix のバージョンを表示する

  ビルド・テスト
    dev:check        型検査のみ実行する（最も速い）
    dev:build        ビルドする（依存解決 + コンパイル）
    dev:test         テストを実行する
    dev:verify       ビルドとテストを順に実行する（コミット前の確認）
    dev:tdd          TDD モード。変更を監視してテストを自動実行する

  実行
    dev:run          main を実行する
    dev:jar          実行可能 JAR を生成する（build-fatjar。依存を同梱）
    dev:jar:run      生成済みの JAR を実行する
    dev:repl         REPL を起動する
    dev:open         ブラウザでアプリケーションを開く（http://localhost:${DEFAULT_APP.port}）

  その他
    dev:format       ソースコードを整形する
    dev:doc          API ドキュメントを生成する
    dev:outdated     更新可能な依存を表示する
    dev:clean        ビルド生成物（build/ artifact/）を削除する
    dev:help         このヘルプを表示する

  対象アプリケーション: ${DEFAULT_APP.label}（apps/${DEFAULT_APP.name}）
`);
    done();
  });
}
