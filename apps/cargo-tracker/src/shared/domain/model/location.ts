/**
 * 位置情報（共有カーネル）。UN/LOCODE で識別される港湾・地点。
 * 全コンテキストで共有する（domain-model Shared Domain）。
 */
const UNLOCODE_PATTERN = /^[A-Z]{5}$/;

export class Location {
  private constructor(readonly unlocode: string) {}

  static of(raw: string): Location {
    const normalized = raw.trim().toUpperCase();
    if (!UNLOCODE_PATTERN.test(normalized)) {
      throw new Error(`不正な UN/LOCODE: ${raw}（5 文字の英字コードが必要です）`);
    }
    return new Location(normalized);
  }

  sameAs(other: Location): boolean {
    return this.unlocode === other.unlocode;
  }
}
