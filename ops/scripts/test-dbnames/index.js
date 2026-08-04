/**
 * テスト用 DB 名の重複検査（IT9）。
 *
 * # なぜ要るか
 *
 * H2 は `DB_CLOSE_DELAY=-1` で起動しており、**同じ名前の DB は JVM の
 * 生存期間ずっと残る**。2 つのテストが同じ名前を使うと、2 回目の
 * フィクスチャ投入が追跡番号の一意制約に当たって落ちる。
 *
 * **落ちるのは名前を再利用した側とは限らない**（実行順に依存する）。
 * IT9 で実際に踏み、失敗メッセージ（一意制約違反）からは
 * 新しく書いたテストの欠陥に見えた。原因に辿り着くまで時間を要した。
 *
 * 名前は各テストが手で付けるため、**重複は必ず起こる**。
 * 実行時に落ちるのを待たず、名前の一覧そのものを検査する
 * （IT8 の Try T2「列挙をやめて正典から導く」と同じ発想）。
 */

import fs from 'fs';
import path from 'path';

const TEST_ROOT = 'apps/cargo-tracker/test';

/** `withApp("name"` の形から DB 名を拾う */
const PATTERN = /with(?:App|AppAndDb|AppLimited|AppInstance)\s*\(\s*"([a-zA-Z0-9_]+)"/g;

/**
 * ディレクトリ配下の .flix を再帰的に集める
 * @param {string} dir 対象ディレクトリ
 * @returns {string[]} ファイルパスの配列
 */
function flixFiles(dir) {
  const found = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) found.push(...flixFiles(full));
    else if (entry.name.endsWith('.flix')) found.push(full);
  }
  return found;
}

/**
 * DB 名の使用箇所を集める
 * @returns {Map<string, string[]>} DB 名 → 使用箇所（ファイル:行）
 */
export function collectDbNames() {
  const uses = new Map();
  for (const file of flixFiles(TEST_ROOT)) {
    const lines = fs.readFileSync(file, 'utf8').split('\n');
    lines.forEach((text, index) => {
      PATTERN.lastIndex = 0;
      let match = PATTERN.exec(text);
      while (match !== null) {
        const name = match[1];
        uses.set(name, [...(uses.get(name) || []), `${file}:${index + 1}`]);
        match = PATTERN.exec(text);
      }
    });
  }
  return uses;
}

/**
 * 重複を報告する
 * @returns {number} 重複した名前の数
 */
export function reportDuplicates() {
  const uses = collectDbNames();
  const duplicates = [...uses.entries()].filter(([, places]) => places.length > 1);

  if (duplicates.length === 0) {
    console.log(`テスト用 DB 名: ${uses.size} 件・重複 0 件`);
    return 0;
  }

  console.log(`テスト用 DB 名の重複: ${duplicates.length} 件\n`);
  for (const [name, places] of duplicates) {
    console.log(`  "${name}"`);
    for (const place of places) console.log(`    ${place}`);
  }
  console.log('\nH2 は DB_CLOSE_DELAY=-1 で JVM の生存期間ずっと残ります。');
  console.log('同じ名前を使うと 2 回目のフィクスチャ投入が一意制約に当たります。');
  return duplicates.length;
}
