'use strict';

/**
 * arch-lint のメタテスト
 *
 * 検査器自身にバグがあれば「検査をパスしているのに違反している」状態が
 * サイレントに発生する。そのためフィクスチャで自己検証する（ADR-0002 の補償策）。
 *
 * - violations/ の各ファイル: 対応する規約で 1 件以上検出されなければならない
 * - conformant/ の各ファイル: 1 件も検出されてはならない
 *
 * **正例の誤検出（偽陽性）は負例の検出漏れと同等に重視する**。
 * 偽陽性は開発者が arch-lint を信用しなくなる直接の原因になるため。
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { lintFiles } from './index.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const FIXTURES = path.join(HERE, 'fixtures');

// ============================================
// 設定
// ============================================

/**
 * フィクスチャは実際のディレクトリ構成を持たないため、
 * ファイル名の「相当パス」コメントからレイヤ・コンテキストを与える。
 */
const FIXTURE_PATHS = {
  'rule01-domain-references-infrastructure.flix': 'src/tracking/domain/model/Bad.flix',
  'rule02-domain-imports-java.flix': 'src/tracking/domain/model/Bad.flix',
  'rule03-application-references-infrastructure.flix': 'src/tracking/application/queryservices/Bad.flix',
  'rule04-cross-context-reference.flix': 'src/tracking/application/queryservices/Bad.flix',
  'rule05-handler-composition-outside-runtime.flix': 'src/tracking/interfaces/web/Bad.flix',
  'rule06-with-handler-in-application.flix': 'src/tracking/application/queryservices/Bad.flix',
  'rule07-rawunsafe-outside-allowlist.flix': 'src/tracking/interfaces/web/Bad.flix',
  'rule08-form-element-directly.flix': 'src/tracking/interfaces/web/Bad.flix',
  'rule09-sql-string-interpolation.flix': 'src/tracking/infrastructure/repositories/Bad.flix',
  'rule08-form-element-multiline.flix': 'src/tracking/interfaces/web/Bad.flix',
  'rule09-sql-interpolation-multiline.flix': 'src/tracking/infrastructure/repositories/Bad.flix',
  'rule01-domain-references-shared.flix': 'src/tracking/domain/port/Ok.flix',
  'rule02-infrastructure-imports-java.flix': 'src/shared/infrastructure/db/Ok.flix',
  'rule03-application-references-domain-port.flix': 'src/tracking/application/queryservices/Ok.flix',
  'rule05-handler-wrapper-in-adapter.flix': 'src/tracking/infrastructure/repositories/Ok.flix',
  'rule06-with-handler-in-infrastructure.flix': 'src/shared/infrastructure/db/Ok.flix',
  'rule07-rawunsafe-in-html-module.flix': 'src/shared/infrastructure/html/Html.flix',
  'rule08-form-in-components.flix': 'src/shared/infrastructure/html/Components.flix',
  'rule09-sql-constant-concatenation.flix': 'src/tracking/infrastructure/repositories/Ok.flix',
  'rule09-sql-multiline-constants.flix': 'src/tracking/infrastructure/repositories/Ok.flix',
  'rule04-same-context-reference.flix': 'src/tracking/application/queryservices/Ok.flix',
};

/**
 * 参照先モジュールのレイヤ解決に使う仮想索引。
 * フィクスチャは単独ファイルのため、参照先が実在しない。
 */
const VIRTUAL_MODULE_INDEX = new Map([
  ['TrackingJdbcReadDb', 'src/tracking/infrastructure/repositories/JdbcReadDb.flix'],
  ['SharedDbPool', 'src/shared/infrastructure/db/Pool.flix'],
  ['BookingCargoRepo', 'src/booking/domain/port/CargoRepo.flix'],
  ['SharedLocation', 'src/shared/domain/model/Location.flix'],
  ['TrackingReadDb', 'src/tracking/domain/port/ReadDb.flix'],
  ['TrackingView', 'src/tracking/domain/port/TrackingView.flix'],
]);

// ============================================
// ヘルパー関数
// ============================================

/**
 * フィクスチャファイルの「相当パス」を返す
 * @param {string} file 実ファイルパス
 * @returns {string} 相当パス
 */
function virtualPathOf(file) {
  const base = path.basename(file);
  const mapped = FIXTURE_PATHS[base];
  if (!mapped) throw new Error(`FIXTURE_PATHS に ${base} の相当パスが未登録です`);
  return mapped;
}

/**
 * ファイル名から期待する規約 ID を取り出す（rule01-xxx.flix → rule01）
 * @param {string} file ファイルパス
 * @returns {string} 規約 ID
 */
function expectedRuleOf(file) {
  return path.basename(file).slice(0, 6);
}

/**
 * フィクスチャを検査する
 *
 * 実ファイルの内容を読みつつ、レイヤ・コンテキスト判定には相当パスを使う。
 * @param {string} file フィクスチャのパス
 * @returns {object[]} 違反の配列
 */
function lintFixture(file) {
  return lintFiles([file], {
    moduleIndex: new Map(VIRTUAL_MODULE_INDEX),
    pathOf: virtualPathOf,
  });
}

// ============================================
// 実行
// ============================================

/**
 * メタテストを実行する
 * @returns {{ passed: number, failed: number, failures: string[] }} 結果
 */
export function runMetaTest() {
  const failures = [];
  let passed = 0;

  // 負例: 対応する規約で検出されなければならない
  const violationFiles = fs.readdirSync(path.join(FIXTURES, 'violations'))
    .map((f) => path.join(FIXTURES, 'violations', f));

  for (const file of violationFiles) {
    const expected = expectedRuleOf(file);
    const found = lintFixture(file);
    const detected = found.some((v) => v.ruleId === expected);
    // 期待外の規約が巻き添えで発火した場合も失敗とする。
    // 検出条件が広がりすぎたことに負例側でも気づけるようにするため。
    const unexpected = [...new Set(found.map((v) => v.ruleId))].filter((id) => id !== expected);
    if (detected && unexpected.length === 0) {
      passed += 1;
    } else if (!detected) {
      failures.push(
        `[負例の検出漏れ] ${path.basename(file)}: ${expected} で検出されるべきですが、` +
        `検出された違反は [${found.map((v) => v.ruleId).join(', ') || 'なし'}] でした`);
    } else {
      failures.push(
        `[負例の巻き添え検出] ${path.basename(file)}: ${expected} 以外に ` +
        `[${unexpected.join(', ')}] が検出されました`);
    }
  }

  // 正例: 1 件も検出されてはならない
  const conformantFiles = fs.readdirSync(path.join(FIXTURES, 'conformant'))
    .map((f) => path.join(FIXTURES, 'conformant', f));

  for (const file of conformantFiles) {
    const found = lintFixture(file);
    if (found.length === 0) {
      passed += 1;
    } else {
      failures.push(
        `[正例の誤検出] ${path.basename(file)}: 検出されてはいけませんが、` +
        found.map((v) => `${v.ruleId}(${v.line} 行)`).join(', ') + ' が検出されました');
    }
  }

  return { passed, failed: failures.length, failures };
}
