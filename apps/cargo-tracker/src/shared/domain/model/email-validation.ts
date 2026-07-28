/**
 * メールアドレスの形式検証（ReDoS 非脆弱・線形時間）。
 *
 * 正規表現 `[^\s@]+@[^\s@]+\.[^\s@]{2,}` は量指定子が重なりバックトラッキングで
 * super-linear になりうる（ReDoS）ため使用しない。文字列走査ベースで線形に判定する。
 */
const MAX_EMAIL_LENGTH = 254; // RFC 5321 の上限

export function isValidEmail(value: string): boolean {
  if (value.length === 0 || value.length > MAX_EMAIL_LENGTH) {
    return false;
  }
  if (/\s/.test(value)) {
    return false;
  }
  const at = value.indexOf('@');
  // '@' が 1 個だけ・先頭以外に存在すること
  if (at <= 0 || at !== value.lastIndexOf('@')) {
    return false;
  }
  const domain = value.slice(at + 1);
  if (domain.length === 0) {
    return false;
  }
  const dot = domain.lastIndexOf('.');
  // ドメインにドットがあり、TLD が 2 文字以上、ドットがドメイン先頭でないこと
  if (dot <= 0 || dot >= domain.length - 2) {
    return false;
  }
  return true;
}
