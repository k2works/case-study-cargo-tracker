'use strict';

/**
 * arch-lint — Flix ソースのアーキテクチャ規約検査
 *
 * ArchUnit が使えない Flix において、レイヤ依存・コンテキスト独立性・
 * ハンドラ適用位置などの規約を機械的に検査する（ADR-0002）。
 *
 * 規約の正典は docs/design/arch_lint_rules.md。検出方法・既知の例外は
 * 実装前に確定させてある（IT1 ふりかえり Try T4）。
 *
 * **レイヤ判定はモジュール名ではなくディレクトリパスで行う**。
 * Flix は同名トップレベルモジュールを複数ファイルで宣言できず、
 * モジュール名がフラットになるためである。
 */

import fs from 'fs';
import path from 'path';

// ============================================
// 設定
// ============================================

/** 検査対象のソースルート */
const SRC_ROOT = 'apps/cargo-tracker/src';

/** Bounded Context の一覧（ディレクトリ名） */
const CONTEXTS = [
  'booking', 'shipper', 'estimation', 'routing',
  'tracking', 'handling', 'billing', 'shared',
];

/** レイヤ名 */
const LAYER = {
  DOMAIN: 'domain',
  APPLICATION: 'application',
  INFRASTRUCTURE: 'infrastructure',
  INTERFACES: 'interfaces',
  COMPOSITION_ROOT: 'composition-root',
  UNKNOWN: 'unknown',
};

/** 規約ごとの既知の例外（ファイルパスの部分一致） */
const EXCEPTIONS = {
  rule05: ['src/composition/'],
  rule07: ['shared/infrastructure/html/Html.flix'],
  rule08: ['shared/infrastructure/html/Components.flix'],
};

/** Flix 標準ライブラリ・JVM の型（レイヤ解決の対象外） */
const KNOWN_EXTERNAL = new Set([
  'Array', 'Assert', 'Bool', 'Char', 'Chain', 'Environment', 'File', 'Float32', 'Float64',
  'Int8', 'Int16', 'Int32', 'Int64', 'Iterator', 'List', 'Map', 'Nel', 'Object', 'Option',
  'Random', 'Ref', 'Result', 'Set', 'String', 'System', 'Vector',
]);

// ============================================
// ヘルパー関数
// ============================================

/**
 * ディレクトリを再帰的に走査して .flix ファイルの一覧を返す
 * @param {string} dir 走査開始ディレクトリ
 * @returns {string[]} ファイルパスの配列
 */
function listFlixFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return listFlixFiles(full);
    return entry.name.endsWith('.flix') ? [full] : [];
  });
}

/**
 * ファイルパスからレイヤを判定する
 * @param {string} filePath ファイルパス
 * @returns {string} レイヤ名
 */
function layerOf(filePath) {
  const p = filePath.replace(/\\/g, '/');
  if (/\/Main\.flix$/.test(p)) return LAYER.COMPOSITION_ROOT;
  // 合成ルートは BC・共有カーネルのいずれにも属さない独立したディレクトリに置く。
  // shared 配下に置くと「shared は BC を参照しない」という規約 10 を検査できない。
  if (p.includes('/composition/')) return LAYER.COMPOSITION_ROOT;
  if (p.includes('/domain/')) return LAYER.DOMAIN;
  if (p.includes('/application/')) return LAYER.APPLICATION;
  if (p.includes('/infrastructure/')) return LAYER.INFRASTRUCTURE;
  if (p.includes('/interfaces/')) return LAYER.INTERFACES;
  return LAYER.UNKNOWN;
}

/**
 * ファイルパスから Bounded Context を判定する
 * @param {string} filePath ファイルパス
 * @returns {string|null} コンテキスト名
 */
function contextOf(filePath) {
  const p = filePath.replace(/\\/g, '/');
  for (const ctx of CONTEXTS) {
    if (p.includes(`/${ctx}/`)) return ctx;
  }
  return null;
}

/**
 * ファイルが規約の例外に該当するか
 * @param {string} filePath ファイルパス
 * @param {string} ruleId 規約 ID
 * @returns {boolean} 例外なら true
 */
function isException(filePath, ruleId) {
  const patterns = EXCEPTIONS[ruleId] || [];
  const p = filePath.replace(/\\/g, '/');
  return patterns.some((pattern) => p.includes(pattern));
}

/**
 * コメント行を除いた行の一覧を返す（行番号つき）
 *
 * IT1 で合成ルートのコメントに `run ... with handler` の記述があり
 * 誤検出の原因になったため、コメント除去を全規約の前提とする。
 * @param {string} source ソースコード
 * @returns {{ lineNumber: number, text: string }[]} 行の配列
 */
function codeLines(source) {
  return source.split('\n')
    .map((text, i) => ({ lineNumber: i + 1, text }))
    .filter(({ text }) => !/^\s*(\/\/\/|\/\/)/.test(text));
}

/**
 * 文字列リテラルを取り除いた行を返す（括弧の対応を数えるための前処理）
 * @param {string} text 行
 * @returns {string} リテラルを除いた行
 */
function withoutStringLiterals(text) {
  return text.replace(/"(?:\\.|[^"\\])*"/g, '""');
}

/**
 * 次の行へ継続する記述か
 *
 * 行末が演算子・区切りで終わる、または括弧が閉じていない場合は継続とみなす。
 * @param {string} text 連結中のテキスト
 * @returns {boolean} 継続するなら true
 */
function continuesToNextLine(text) {
  const code = withoutStringLiterals(text);
  // `{` は mod / def のブロックにも使われ、ファイル全体が 1 論理行に潰れてしまう。
  // 式の継続を見るには丸括弧と角括弧で十分である。
  const opens = (code.match(/[([]/g) || []).length;
  const closes = (code.match(/[)\]]/g) || []).length;
  if (opens > closes) return true;
  return /(\+|,|->|::|=)\s*$/.test(code.trim());
}

/**
 * コメントを除いたソースを「論理行」へ畳む
 *
 * 行単位の走査では、同じ違反を複数行に分けて書くだけで検出をすり抜ける
 * （`"SELECT ... " +` で改行する本プロジェクトの SQL の書き方がまさにその形）。
 * 継続行を 1 つの論理行へ結合してから照合することで、この抜け道を塞ぐ。
 * 行番号は結合を開始した行を用いる。
 * @param {string} source ソースコード
 * @returns {{ lineNumber: number, text: string }[]} 論理行の配列
 */
function logicalLines(source) {
  const result = [];
  let buffer = null;
  for (const { lineNumber, text } of codeLines(source)) {
    if (buffer === null) {
      if (text.trim() === '') continue;
      buffer = { lineNumber, text: text.trim() };
    } else {
      buffer.text += ' ' + text.trim();
    }
    if (!continuesToNextLine(buffer.text)) {
      result.push(buffer);
      buffer = null;
    }
  }
  if (buffer !== null) result.push(buffer);
  return result;
}

/**
 * ソースから参照しているモジュール名を抽出する
 *
 * `use Foo.bar` / `use Foo.{a, b}` / `Foo.bar(...)` の 3 形式を対象とする。
 * @param {string} source ソースコード
 * @returns {Set<string>} モジュール名の集合
 */
function referencedModules(source) {
  const modules = new Set();
  const lines = codeLines(source);
  const selfModules = declaredModules(source);

  for (const { text } of lines) {
    const useMatch = text.match(/^\s*use\s+([A-Z][A-Za-z0-9_]*)/);
    if (useMatch) modules.add(useMatch[1]);

    for (const m of text.matchAll(/\b([A-Z][A-Za-z0-9_]*)\.[a-zA-Z]/g)) {
      modules.add(m[1]);
    }
  }
  // 自分自身の宣言・標準ライブラリは対象外
  for (const own of selfModules) modules.delete(own);
  for (const ext of KNOWN_EXTERNAL) modules.delete(ext);
  return modules;
}

/**
 * ソースが宣言しているモジュール名を抽出する
 * @param {string} source ソースコード
 * @returns {string[]} モジュール名の配列
 */
function declaredModules(source) {
  return codeLines(source)
    .map(({ text }) => text.match(/^\s*mod\s+([A-Za-z][A-Za-z0-9_.]*)/))
    .filter(Boolean)
    .map((m) => m[1]);
}

/**
 * 「モジュール名 → ファイルパス」の索引を作る
 * @param {string[]} files ファイルパスの配列
 * @returns {Map<string, string>} 索引
 */
function buildModuleIndex(files) {
  const index = new Map();
  for (const file of files) {
    const source = fs.readFileSync(file, 'utf8');
    for (const mod of declaredModules(source)) index.set(mod, file);
  }
  return index;
}

/**
 * 違反を作る
 * @param {string} ruleId 規約 ID
 * @param {string} file ファイルパス
 * @param {number} line 行番号
 * @param {string} message メッセージ
 * @returns {object} 違反
 */
function violation(ruleId, file, line, message) {
  return { ruleId, file, line, message };
}

// ============================================
// 規約の実装
// ============================================

/**
 * 規約 1・3: レイヤ間の依存方向
 * @param {object} ctx 検査コンテキスト
 * @returns {object[]} 違反の配列
 */
function ruleLayerDependencies(ctx) {
  const violations = [];
  const forbidden = {
    [LAYER.DOMAIN]: { layers: [LAYER.INFRASTRUCTURE, LAYER.INTERFACES], ruleId: 'rule01' },
    [LAYER.APPLICATION]: { layers: [LAYER.INFRASTRUCTURE], ruleId: 'rule03' },
  };

  for (const { file, source, layer } of ctx.files) {
    const rule = forbidden[layer];
    if (!rule) continue;

    for (const mod of referencedModules(source)) {
      const target = ctx.moduleIndex.get(mod);
      if (!target) continue;
      const targetLayer = layerOf(target);
      if (rule.layers.includes(targetLayer)) {
        const line = findLine(source, mod);
        violations.push(violation(rule.ruleId, file, line,
          `${layer} 層のファイルが ${targetLayer} 層のモジュール ${mod} を参照しています`));
      }
    }
  }
  return violations;
}

/**
 * 規約 2: domain 層が java を import しない
 * @param {object} ctx 検査コンテキスト
 * @returns {object[]} 違反の配列
 */
function ruleDomainNoJava(ctx) {
  const violations = [];
  for (const { file, source, layer } of ctx.files) {
    if (layer !== LAYER.DOMAIN) continue;
    for (const { lineNumber, text } of codeLines(source)) {
      if (/^\s*import\s+(java|javax)\./.test(text)) {
        violations.push(violation('rule02', file, lineNumber,
          `domain 層のファイルが Java を import しています: ${text.trim()}`));
      }
    }
  }
  return violations;
}

/**
 * 規約 4: Bounded Context 間の直接参照
 * @param {object} ctx 検査コンテキスト
 * @returns {object[]} 違反の配列
 */
function ruleContextIsolation(ctx) {
  const violations = [];
  for (const { file, source, context } of ctx.files) {
    if (!context || context === 'shared') continue;

    for (const mod of referencedModules(source)) {
      const target = ctx.moduleIndex.get(mod);
      if (!target) continue;
      const targetContext = contextOf(target);
      if (targetContext && targetContext !== context && targetContext !== 'shared') {
        const line = findLine(source, mod);
        violations.push(violation('rule04', file, line,
          `${context} コンテキストが ${targetContext} コンテキストのモジュール ${mod} を直接参照しています`));
      }
    }
  }
  return violations;
}

/**
 * 規約 5: 効果ハンドラの合成は合成ルートとテストにのみ現れる
 *
 * 「ハンドラ適用関数の呼び出しが 2 段以上入れ子」を検出する。
 * 単一のハンドラを定義・適用するラップ関数は検出しない。
 * @param {object} ctx 検査コンテキスト
 * @returns {object[]} 違反の配列
 */
function ruleHandlerComposition(ctx) {
  const violations = [];
  // with<Name>(...) や readOnly(...) の引数にさらに with<Name>( が現れる形
  const compositionPattern = /\b(with[A-Z][A-Za-z0-9]*|readOnly|transactional)\s*\([^)]*\(\s*\)\s*->\s*(with[A-Z][A-Za-z0-9]*|readOnly|transactional)\s*\(/;

  for (const { file, source } of ctx.files) {
    if (isException(file, 'rule05')) continue;
    for (const { lineNumber, text } of logicalLines(source)) {
      if (compositionPattern.test(text)) {
        violations.push(violation('rule05', file, lineNumber,
          `合成ルート以外で効果ハンドラを合成しています: ${text.trim()}`));
      }
    }
  }
  return violations;
}

/**
 * 規約 6: domain / application / interfaces にハンドラ適用が現れない
 * @param {object} ctx 検査コンテキスト
 * @returns {object[]} 違反の配列
 */
function ruleNoHandlerInUpperLayers(ctx) {
  const violations = [];
  const target = [LAYER.DOMAIN, LAYER.APPLICATION, LAYER.INTERFACES];

  for (const { file, source, layer } of ctx.files) {
    if (!target.includes(layer)) continue;
    for (const { lineNumber, text } of codeLines(source)) {
      if (/\bwith\s+handler\b/.test(text)) {
        violations.push(violation('rule06', file, lineNumber,
          `${layer} 層で効果ハンドラを適用しています。インフラ層へ移してください`));
      }
    }
  }
  return violations;
}

/**
 * 規約 7: Html.RawUnsafe の使用箇所が許可リストに含まれる
 * @param {object} ctx 検査コンテキスト
 * @returns {object[]} 違反の配列
 */
function ruleRawUnsafeAllowlist(ctx) {
  const violations = [];
  for (const { file, source } of ctx.files) {
    if (isException(file, 'rule07')) continue;
    for (const { lineNumber, text } of codeLines(source)) {
      if (/\bRawUnsafe\b/.test(text)) {
        violations.push(violation('rule07', file, lineNumber,
          'RawUnsafe は許可リスト外では使用できません。エスケープされる Text を使ってください'));
      }
    }
  }
  return violations;
}

/**
 * 規約 8: 状態を変える form を直接構築しない（Components.form を使う）
 *
 * この規約の目的は **CSRF トークンの付け忘れを防ぐ**こと。CSRF は
 * 「他サイトから利用者の権限で状態を変えさせられる」攻撃であり、
 * 状態を変えない GET フォーム（絞り込み・検索）には当てはまらない。
 *
 * GET フォームにトークンを付けると、意味のない `_csrf` が URL のクエリへ載る。
 * **要らない防御を足すと、要る防御との区別が付かなくなる。**
 *
 * 免除するのは `method="get"` を明示したものだけ。HTML の `form` は
 * `method` を省くと GET になるが、**書き手がそれを意図したか読み取れない**
 * （POST のつもりで書き忘れた可能性を区別できない）。
 *
 * @param {object} ctx 検査コンテキスト
 * @returns {object[]} 違反の配列
 */
function ruleFormViaComponents(ctx) {
  const violations = [];
  const formPattern = /(element|Html\.Element)\s*\(\s*"form"/;
  const explicitGet = /attr\s*\(\s*"method"\s*,\s*"get"\s*\)/i;

  for (const { file, source } of ctx.files) {
    if (isException(file, 'rule08')) continue;
    for (const { lineNumber, text } of logicalLines(source)) {
      if (formPattern.test(text) && !explicitGet.test(text)) {
        violations.push(violation('rule08', file, lineNumber,
          '状態を変える form は Components.form 経由で生成してください' +
          '（CSRF トークンの付け忘れを防ぐため）。' +
          '状態を変えない検索フォームは attr("method", "get") を明示してください'));
      }
    }
  }
  return violations;
}

/**
 * 規約 9: SQL 文字列に変数を補間しない
 *
 * 定数同士の `+` 連結は可読性のため許容し、`${...}` による変数埋め込みのみを禁止する。
 * @param {object} ctx 検査コンテキスト
 * @returns {object[]} 違反の配列
 */
function ruleNoSqlInterpolation(ctx) {
  const violations = [];
  // SQL キーワードを含む文字列と、変数補間を含む文字列が同一の式に現れる形を検出する。
  // 1 リテラル内に閉じた検査では `"SELECT ... " + "WHERE x = '${v}'"` を見逃す。
  //
  // **単語の一致だけでは足りない**。`select` は HTML の要素名でもあり、
  // `element("select", ...)` が SQL と誤認された（IT6 で実際に発生）。
  // SQL の骨格（`SELECT ... FROM` / `INSERT INTO` / `UPDATE ... SET` /
  // `DELETE FROM` / `MERGE INTO`）を要求すれば、要素名とは区別できる。
  //
  // 骨格が 2 つの文字列リテラルに分かれる形（`"SELECT x " + "FROM y"`）も
  // 拾えるよう、論理行全体に対して照合する。
  const sqlSkeleton =
    /\bSELECT\b[\s\S]*\bFROM\b|\bINSERT\s+INTO\b|\bUPDATE\b[\s\S]*\bSET\b|\bDELETE\s+FROM\b|\bMERGE\s+INTO\b/i;
  // 骨格を別の関数へ切り出して連結する形（本リポジトリの JDBC アダプタが
  // 実際に採っている書き方）を拾う。`selectColumns() + "WHERE x = '${n}'"` は
  // 骨格が同じ論理行に現れないため、上の照合だけでは**素通しになる**
  // （IT6 レビュー。規約が守らせたい書き方をしているファイルほど検査から外れていた）。
  const sqlFragmentCall = /\b(select|insert|update|delete|merge)[A-Za-z]*(Sql|Columns|Clause|Fragment)\s*\(/i;
  // 断片と連結される SQL 句。要素名との衝突を避けるため句のみを見る
  const sqlClause = /\b(WHERE|VALUES|ORDER\s+BY|GROUP\s+BY|HAVING|SET|LIMIT)\b/i;
  const interpolation = /"[^"]*\$\{[^"]*"/;

  for (const { file, source } of ctx.files) {
    for (const { lineNumber, text } of logicalLines(source)) {
      const looksLikeSql =
        sqlSkeleton.test(text) ||
        (sqlFragmentCall.test(text) && sqlClause.test(text));
      if (looksLikeSql && interpolation.test(text)) {
        violations.push(violation('rule09', file, lineNumber,
          'SQL に変数を文字列補間しています。PreparedStatement のプレースホルダを使ってください'));
      }
    }
  }
  return violations;
}

/**
 * 規約 10: shared は Bounded Context を参照しない
 *
 * 共有カーネルが特定の BC に依存すると、その BC を変更するたびに全 BC が影響を受ける。
 * 合成ルート（`src/composition/`）と `Main.flix` は BC を配線するのが役目のため対象外。
 * @param {object} ctx 検査コンテキスト
 * @returns {object[]} 違反の配列
 */
function ruleSharedDoesNotReferenceContext(ctx) {
  const violations = [];

  for (const { file, source } of ctx.files) {
    if (contextOf(file) !== 'shared') continue;
    if (layerOf(file) === LAYER.COMPOSITION_ROOT) continue;

    for (const name of referencedModules(source)) {
      const target = ctx.moduleIndex.get(name);
      if (!target) continue;
      const targetContext = contextOf(target);
      if (targetContext && targetContext !== 'shared') {
        violations.push(violation('rule10', file, findLine(source, name),
          `共有カーネルが Bounded Context (${targetContext}) のモジュール ${name} を参照しています`));
      }
    }
  }
  return violations;
}

/**
 * 規約 11: 合成ルートの BC 間翻訳は `src/composition/acl/` にのみ置く
 *
 * ADR-0011。規約 4（BC 間の直接参照）は `src/composition/` を対象外にしているため、
 * **合成ルートに翻訳を書くと検出できない穴になる**（ADR-0010 で実際にそうなった）。
 *
 * 穴を塞ぐのではなく、**穴に名前を付けて数えられるようにする**のが本規約である。
 * `composition/acl/` のファイル数が、そのまま BC 間の翻訳の件数になる。
 *
 * **例外は許可リストで書く**。`composition/acl/`（翻訳の置き場所）と
 * `Composition.flix`（ルーティング表そのもの。全 BC の `Routes` を参照するのが役目）の
 * 2 つだけである。`shared` は数えない（どの BC からも参照してよい）。
 *
 * 当初は「対象を `*Wiring.flix` に限る」形で書いていたが、これは**仕様より緩く、
 * 命名だけで回避できた**（IT9 レビュー M1）。`src/composition/Bridges.flix` に
 * 翻訳を書けば規約 4 にも 11 にもかからない。穴を数えられるようにするのが
 * 本規約の眼目なので、数える対象から漏れる経路を残してはいけない。
 * @param {object} ctx 検査コンテキスト
 * @returns {object[]} 違反の配列
 */
function ruleCompositionAclOnly(ctx) {
  const violations = [];

  for (const { file, source } of ctx.files) {
    const normalized = file.split(path.sep).join('/');
    if (!normalized.includes('/composition/')) continue;
    // 翻訳の置き場所。ここは 2 つ以上の BC を参照してよい
    if (normalized.includes('/composition/acl/')) continue;
    // ルーティング表は全 BC を知るのが役目（翻訳は持たない）
    if (normalized.endsWith('/Composition.flix')) continue;

    const contexts = new Set();
    for (const name of referencedModules(source)) {
      const target = ctx.moduleIndex.get(name);
      if (!target) continue;
      const targetContext = contextOf(target);
      if (targetContext && targetContext !== 'shared') contexts.add(targetContext);
    }

    if (contexts.size >= 2) {
      const listed = [...contexts].sort().join(', ');
      violations.push(violation('rule11', file, 1,
        `composition のファイルが複数の Bounded Context (${listed}) を参照しています。`
        + 'BC 間の翻訳は src/composition/acl/ に置いてください（ADR-0011）'));
    }
  }
  return violations;
}

/**
 * ソース中でモジュール名が最初に現れる行番号を返す
 * @param {string} source ソースコード
 * @param {string} moduleName モジュール名
 * @returns {number} 行番号
 */
function findLine(source, moduleName) {
  const found = codeLines(source).find(({ text }) =>
    new RegExp(`\\b${moduleName}\\b`).test(text));
  return found ? found.lineNumber : 1;
}

/** 全規約 */
const RULES = [
  ruleLayerDependencies,
  ruleDomainNoJava,
  ruleContextIsolation,
  ruleHandlerComposition,
  ruleNoHandlerInUpperLayers,
  ruleRawUnsafeAllowlist,
  ruleFormViaComponents,
  ruleNoSqlInterpolation,
  ruleSharedDoesNotReferenceContext,
  ruleCompositionAclOnly,
];

// ============================================
// 実行
// ============================================

/**
 * 指定したファイル群を検査する
 * @param {string[]} files 検査対象のファイルパス
 * @param {object} [options] オプション
 * @param {Map<string, string>} [options.moduleIndex] モジュール索引（省略時は files から構築）
 * @returns {object[]} 違反の配列
 */
export function lintFiles(files, options = {}) {
  const moduleIndex = options.moduleIndex || buildModuleIndex(files);
  const ctx = {
    moduleIndex,
    files: files.map((file) => {
      // 論理パス: 例外判定とレイヤ判定に使う。
      // メタテストのフィクスチャは実際のディレクトリ構成を持たないため、
      // 相当パスを渡せるようにしている。
      const logicalPath = options.pathOf ? options.pathOf(file) : file;
      return {
        file: logicalPath,
        realFile: file,
        source: fs.readFileSync(file, 'utf8'),
        layer: layerOf(logicalPath),
        context: contextOf(logicalPath),
      };
    }),
  };
  return RULES.flatMap((rule) => rule(ctx));
}

/**
 * プロジェクト全体を検査する
 * @param {string} [root] プロジェクトルート
 * @returns {object[]} 違反の配列
 */
export function lintProject(root = process.cwd()) {
  const srcDir = path.join(root, SRC_ROOT);
  const files = listFlixFiles(srcDir);
  return lintFiles(files);
}

export { listFlixFiles, layerOf, contextOf, buildModuleIndex, LAYER };
