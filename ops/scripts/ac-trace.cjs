'use strict';
// TST-06：ユーザーストーリーの受入条件（AC）と、テスト名の AC 番号を突き合わせる。
// 使い方：node ops/scripts/ac-trace.cjs US02=AC-01+AC-02,US04=AC-01+AC-02
//   ストーリーと、今回の対象にする AC を指定する（=以降を省くと、そのストーリーの全 AC）。省くと全ストーリーの全 AC。
//   対象外の AC は一覧に出すが、不足とは数えない。
// 終了コード：対象の AC に、対応するテストが無ければ 1（テストの無い AC を一覧にする）。
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '../..');
const STORY_FILE = path.join(ROOT, 'docs/requirements/user_story.md');
const TEST_ROOTS = ['modules', 'apps'];
const TEST_FILE = /(Spec\.scala|\.test\.tsx?|\.spec\.ts)$/;
const SKIP_DIRS = new Set(['node_modules', 'target', 'dist', '.git', 'test-results']);

/** ストーリーごとの AC 番号を取り出す。 */
function parseStories(text) {
  const stories = {};
  let current = null;
  for (const line of text.split('\n')) {
    const head = /^## (US\d+):/.exec(line);
    if (head) { current = head[1]; stories[current] = []; continue; }
    const ac = /^- \[[ x]\] \*\*(AC-\d+)\*\*/.exec(line);
    if (ac && current) stories[current].push(ac[1]);
  }
  return stories;
}

function* walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) yield* walk(full);
    else if (TEST_FILE.test(entry.name)) yield full;
  }
}

/** テスト名（test("…") / it("…") / test('…')）にある「USxx AC-yy」を拾う。コメントや本文の言及は数えない。 */
function collectCoverage() {
  const covered = {};
  const call = /\b(?:test|it|describe|ignore)\s*\(\s*(?:s?"([^"]*)"|'([^']*)'|`([^`]*)`)/g;
  // 「US04-AC01・AC02」「US04 AC-01」のどちらの書き方も受ける（AC 番号は 2 桁にそろえて比べる）
  const ref = /(US\d+)[ -]+((?:AC-?\d+[・、, ]*)+)/g;
  for (const root of TEST_ROOTS) {
    const dir = path.join(ROOT, root);
    if (!fs.existsSync(dir)) continue;
    for (const file of walk(dir)) {
      const text = fs.readFileSync(file, 'utf8');
      for (const m of text.matchAll(call)) {
        const name = m[1] ?? m[2] ?? m[3] ?? '';
        for (const r of name.matchAll(ref)) {
          for (const n of r[2].matchAll(/AC-?(\d+)/g)) {
            (covered[`${r[1]} AC-${n[1].padStart(2, '0')}`] ??= new Set()).add(path.relative(ROOT, file));
          }
        }
      }
    }
  }
  return covered;
}

function main() {
  const wanted = Object.fromEntries(
    (process.argv[2] ?? '').split(',').filter(Boolean).map((t) => {
      const [us, acs] = t.split('=');
      return [us, acs ? acs.split('+') : null];
    }),
  );
  const stories = parseStories(fs.readFileSync(STORY_FILE, 'utf8'));
  const covered = collectCoverage();
  const targets = Object.keys(wanted).length ? Object.keys(wanted) : Object.keys(stories);
  let missing = 0;
  for (const us of targets) {
    if (!stories[us]) { console.log(`${us}: ストーリーが見つからない`); missing++; continue; }
    for (const ac of stories[us]) {
      const files = covered[`${us} ${ac}`];
      const inScope = !wanted[us] || wanted[us].includes(ac);
      if (!inScope && !files) console.log(`対象外  ${us} ${ac}`);
      else if (files) console.log(`OK      ${us} ${ac}  ${[...files].join(', ')}`);
      else { console.log(`MISSING ${us} ${ac}  テストなし`); missing++; }
    }
  }
  if (missing) console.log(`\nテストの無い AC：${missing} 件`);
  process.exit(missing ? 1 : 0);
}

if (require.main === module) main();
module.exports = { parseStories, collectCoverage };
