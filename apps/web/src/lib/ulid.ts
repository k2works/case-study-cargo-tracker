const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const ULID_PATTERN = /^[0-7][0-9A-HJKMNP-TV-Z]{25}$/;

/** 集約 ID（ULID）の形式か。サーバー（Scala の `Ulid.parse`）と同じ規則。 */
export const isUlid = (value: string): boolean => ULID_PATTERN.test(value);

/**
 * ULID を生成する（ADR-003：集約を新規作成するコマンドの ID は、呼び出し側が生成して渡す）。
 * 時刻と乱数は引数にして、テストで再現できるようにする。画面から `Date.now()` を直接呼ばない（BE-11・FE 7.2）。
 */
export function generateUlid(now: () => Date = () => new Date(), random: () => number = () => crypto.getRandomValues(new Uint8Array(1))[0]! / 256): string {
  let ms = now().getTime();
  let time = '';
  for (let i = 0; i < 10; i++) {
    time = ALPHABET[ms % 32]! + time;
    ms = Math.floor(ms / 32);
  }
  let rand = '';
  for (let i = 0; i < 16; i++) rand += ALPHABET[Math.floor(random() * 32)]!;
  return time + rand;
}
