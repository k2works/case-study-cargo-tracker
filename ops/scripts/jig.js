'use strict';

/**
 * JIG (dddjava/jig) 設計可視化タスク
 *
 * Scala のコンパイル済みバイトコードを解析し、パッケージ関連図・ドメインモデル図・
 * ユースケース図などの設計ドキュメントを HTML で生成する。
 *
 * 前提:
 *   - graphviz (dot) がインストール済みであること
 *   - ops/tools/jig/jig-cli.jar が配置済みであること
 *   - 設定は apps/cargo-tracker/jig.properties と application.properties を参照
 *
 * タスク:
 *   - jig:setup         jig-cli.jar をダウンロード（未取得の場合）
 *   - jig:report        コンパイル + JIG ドキュメント生成
 *   - jig:report:only   コンパイルをスキップして JIG のみ実行
 *   - jig:open          生成した index.html をブラウザで開く
 */

import path from 'path';
import fs from 'fs';
import { execSync } from 'child_process';

const APP_DIR = 'apps/cargo-tracker';
const JAR_PATH = 'ops/tools/jig/jig-cli.jar';
const JIG_VERSION = process.env.JIG_VERSION || '2026.6.3';
const JAR_URL = `https://github.com/dddjava/jig/releases/download/${JIG_VERSION}/jig-cli.jar`;
const OUTPUT_INDEX = 'apps/cargo-tracker/build/jig/index.html';

/** jig-cli.jar を未取得なら GitHub Releases から取得 */
function setupJar() {
  const jarAbs = path.join(process.cwd(), JAR_PATH);
  if (fs.existsSync(jarAbs)) {
    console.log(`[jig] jar は取得済み: ${JAR_PATH}`);
    return;
  }
  fs.mkdirSync(path.dirname(jarAbs), { recursive: true });
  console.log(`[jig] jig-cli.jar (${JIG_VERSION}) をダウンロードしています...`);
  execSync(`curl -sL -o "${jarAbs}" "${JAR_URL}"`, { stdio: 'inherit' });
  console.log(`[jig] 取得完了: ${JAR_PATH}`);
}

/** dot (graphviz) の有無を確認 */
function checkGraphviz() {
  try {
    execSync('dot -V', { stdio: 'ignore' });
  } catch (e) {
    throw new Error('graphviz (dot) が見つかりません。`brew install graphviz` を実行してください。');
  }
}

/** JIG を実行 */
function runJig() {
  checkGraphviz();
  setupJar();
  const jarAbs = path.join(process.cwd(), JAR_PATH);
  const appAbs = path.join(process.cwd(), APP_DIR);
  console.log('[jig] JIG ドキュメントを生成しています...');
  execSync(`java -jar "${jarAbs}"`, { cwd: appAbs, stdio: 'inherit' });
  console.log(`[jig] 完了: ${OUTPUT_INDEX}`);
}

export default function (gulp) {
  gulp.task('jig:setup', (done) => {
    setupJar();
    done();
  });

  gulp.task('jig:report:only', (done) => {
    runJig();
    done();
  });

  gulp.task('jig:report', (done) => {
    const appAbs = path.join(process.cwd(), APP_DIR);
    console.log('[jig] sbt compile を実行しています...');
    execSync('sbt compile', { cwd: appAbs, stdio: 'inherit' });
    runJig();
    done();
  });

  gulp.task('jig:open', (done) => {
    const indexAbs = path.join(process.cwd(), OUTPUT_INDEX);
    if (!fs.existsSync(indexAbs)) {
      throw new Error(`${OUTPUT_INDEX} が見つかりません。先に \`gulp jig:report\` を実行してください。`);
    }
    const opener = process.platform === 'darwin' ? 'open' : process.platform === 'win32' ? 'start' : 'xdg-open';
    execSync(`${opener} "${indexAbs}"`, { stdio: 'ignore' });
    done();
  });
}
